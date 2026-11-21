package com.nexustools.genericui.plugin

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

/**
 * Partitions monolithic Windows COFF (standard and `-bigobj`) object files produced for
 * `platform.*` static caches into fine-grained connected-component COFF `.o` members inside a
 * GNU/LLVM `.a` static archive.
 *
 * Grouping related sections (function/data + unwind `.pdata`/`.xdata` + COMDAT associative
 * sections + static/internal symbol targets) into separate archive members allows `ld.lld` to
 * pull only referenced functions and their direct dependencies—preventing unreferenced wrapper
 * sections from dragging unused MinGW `.idata` PE imports (such as `_getdllprocaddr` or
 * `WaitOnAddress`) into output DLLs and executables.
 */
internal object WindowsCoffObjectPartitioner {

    private const val IMAGE_SCN_CNT_UNINITIALIZED_DATA = 0x00000080
    private const val IMAGE_SCN_LNK_NRELOC_OVFL = 0x01000000
    private const val IMAGE_SYM_CLASS_EXTERNAL = 2
    private const val IMAGE_SYM_CLASS_STATIC = 3
    private const val IMAGE_SYM_CLASS_FILE = 103
    private const val IMAGE_COMDAT_SELECT_ASSOCIATIVE = 5

    private data class CoffReloc(
        val rva: Int,
        val symSlot: Int,
        val type: Int
    )

    private data class CoffSection(
        val index: Int, // 1-based
        val name: String,
        val sizeOfRawData: Int,
        val rawData: ByteArray,
        val characteristics: Int,
        val relocs: List<CoffReloc>
    )

    private data class CoffSymbol(
        val slot: Int,
        val name: String,
        val value: Int,
        val secNum: Int,
        val type: Int,
        val storageClass: Int,
        val auxCount: Int,
        val checkSum: Int,
        val assocSec: Int,
        val selection: Int
    )

    fun partitionObjectsIntoArchive(
        objectFiles: List<File>,
        llvmArExecutable: File,
        targetArchive: File
    ) {
        targetArchive.parentFile?.mkdirs()
        if (targetArchive.exists()) {
            targetArchive.delete()
        }

        val arMagic = "!<arch>\n".toByteArray(Charsets.US_ASCII)
        var memberIndex = 0
        BufferedOutputStream(targetArchive.outputStream(), 1 shl 20).use { out ->
            out.write(arMagic)
            for (objFile in objectFiles) {
                val members = partitionSingleCoffObject(objFile.readBytes())
                for (memberBytes in members) {
                    val memberName = String.format(Locale.ROOT, "m%08x.o/", memberIndex++)
                    val header = buildString(60) {
                        append(memberName.padEnd(16, ' '))
                        append("0".padEnd(12, ' '))
                        append("0".padEnd(6, ' '))
                        append("0".padEnd(6, ' '))
                        append("100644".padEnd(8, ' '))
                        append(memberBytes.size.toString().padEnd(10, ' '))
                        append("`\n")
                    }
                    val hdrBytes = header.toByteArray(Charsets.US_ASCII)
                    check(hdrBytes.size == 60) {
                        "Invalid ar member header length: ${hdrBytes.size}"
                    }
                    out.write(hdrBytes)
                    out.write(memberBytes)
                    if ((memberBytes.size and 1) == 1) {
                        out.write('\n'.code)
                    }
                }
            }
        }

        val pb = ProcessBuilder(llvmArExecutable.absolutePath, "s", targetArchive.absolutePath)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val toolOut = proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
        val exit = proc.waitFor()
        if (exit != 0) {
            error("llvm-ar s failed with exit code $exit on ${targetArchive.absolutePath}:\n$toolOut")
        }
    }

    internal fun partitionSingleCoffObject(bytes: ByteArray): List<ByteArray> {
        if (bytes.size < 20) return emptyList()

        val w0 = readU16LE(bytes, 0)
        val w1 = readU16LE(bytes, 2)
        val isBigObj = w0 == 0 && w1 == 0xFFFF

        val actualMachine: Int
        val numSections: Int
        val ptrSymTable: Int
        val numSymbols: Int
        val sectionTableOffset: Int
        val symEntrySize: Int

        if (isBigObj) {
            if (bytes.size < 56) return emptyList()
            actualMachine = readU16LE(bytes, 6)
            numSections = readU32LE(bytes, 44)
            ptrSymTable = readU32LE(bytes, 48)
            numSymbols = readU32LE(bytes, 52)
            sectionTableOffset = 56
            symEntrySize = 20
        } else {
            actualMachine = w0
            numSections = w1
            ptrSymTable = readU32LE(bytes, 8)
            numSymbols = readU32LE(bytes, 12)
            val optHdrSize = readU16LE(bytes, 16)
            sectionTableOffset = 20 + optHdrSize
            symEntrySize = 18
        }

        if (numSections <= 0 || ptrSymTable <= 0 || numSymbols <= 0) {
            return emptyList()
        }

        val stringTableOffset = ptrSymTable + numSymbols * symEntrySize

        val sections = ArrayList<CoffSection>(numSections + 1)
        sections.add(CoffSection(0, "", 0, ByteArray(0), 0, emptyList())) // 1-based sentinel

        for (sIdx in 1..numSections) {
            val secOff = sectionTableOffset + (sIdx - 1) * 40
            val secName = decodeSectionName(bytes, secOff, stringTableOffset)
            val sizeOfRawData = readU32LE(bytes, secOff + 16)
            val ptrRawData = readU32LE(bytes, secOff + 20)
            val ptrRelocs = readU32LE(bytes, secOff + 24)
            val numRelocsU16 = readU16LE(bytes, secOff + 32)
            val characteristics = readU32LE(bytes, secOff + 36)

            val isBss = (characteristics and IMAGE_SCN_CNT_UNINITIALIZED_DATA) != 0
            val rawData = if (!isBss && sizeOfRawData > 0 && ptrRawData > 0 && ptrRawData + sizeOfRawData <= bytes.size) {
                bytes.copyOfRange(ptrRawData, ptrRawData + sizeOfRawData)
            } else {
                ByteArray(0)
            }

            val relocs: List<CoffReloc> = if (ptrRelocs > 0 && (numRelocsU16 > 0 || (characteristics and IMAGE_SCN_LNK_NRELOC_OVFL) != 0)) {
                val hasOvfl = (characteristics and IMAGE_SCN_LNK_NRELOC_OVFL) != 0 && numRelocsU16 == 0xFFFF
                val totalCount: Int
                val firstRelocOff: Int
                if (hasOvfl) {
                    val ovflTotal = readU32LE(bytes, ptrRelocs)
                    totalCount = (ovflTotal - 1).coerceAtLeast(0)
                    firstRelocOff = ptrRelocs + 10
                } else {
                    totalCount = numRelocsU16
                    firstRelocOff = ptrRelocs
                }
                val list = ArrayList<CoffReloc>(totalCount)
                for (r in 0 until totalCount) {
                    val rOff = firstRelocOff + r * 10
                    if (rOff + 10 > bytes.size) break
                    list.add(
                        CoffReloc(
                            rva = readU32LE(bytes, rOff),
                            symSlot = readU32LE(bytes, rOff + 4),
                            type = readU16LE(bytes, rOff + 8)
                        )
                    )
                }
                list
            } else {
                emptyList()
            }

            sections.add(
                CoffSection(
                    index = sIdx,
                    name = secName,
                    sizeOfRawData = sizeOfRawData,
                    rawData = rawData,
                    characteristics = characteristics,
                    relocs = relocs
                )
            )
        }

        val symBySlot = arrayOfNulls<CoffSymbol>(numSymbols)
        val secDefBySection = arrayOfNulls<CoffSymbol>(numSections + 1)
        val symbolsBySection = Array<MutableList<CoffSymbol>>(numSections + 1) { mutableListOf() }
        var feat00Symbol: CoffSymbol? = null

        var slot = 0
        while (slot < numSymbols) {
            val off = ptrSymTable + slot * symEntrySize
            if (off + symEntrySize > bytes.size) break

            val nameZero = readU32LE(bytes, off)
            val symName = if (nameZero != 0) {
                readFixedCString(bytes, off, 8)
            } else {
                val strOff = stringTableOffset + readU32LE(bytes, off + 4)
                readNullTerminatedString(bytes, strOff)
            }

            val value = readU32LE(bytes, off + 8)
            val secNum: Int
            val type: Int
            val storageClass: Int
            val auxCount: Int
            if (isBigObj) {
                secNum = readU32LE(bytes, off + 12)
                type = readU16LE(bytes, off + 16)
                storageClass = bytes[off + 18].toInt() and 0xFF
                auxCount = bytes[off + 19].toInt() and 0xFF
            } else {
                secNum = readI16LE(bytes, off + 12)
                type = readU16LE(bytes, off + 14)
                storageClass = bytes[off + 16].toInt() and 0xFF
                auxCount = bytes[off + 17].toInt() and 0xFF
            }

            var checkSum = 0
            var assocSec = 0
            var selection = 0
            val isSecDef = storageClass == IMAGE_SYM_CLASS_STATIC &&
                auxCount >= 1 &&
                secNum in 1..numSections
            if (isSecDef) {
                val auxOff = off + symEntrySize
                if (auxOff + 18 <= bytes.size) {
                    checkSum = readU32LE(bytes, auxOff + 8)
                    selection = bytes[auxOff + 14].toInt() and 0xFF
                    assocSec = if (isBigObj && auxOff + 18 <= bytes.size) {
                        readU16LE(bytes, auxOff + 12) or (readU16LE(bytes, auxOff + 16) shl 16)
                    } else {
                        readU16LE(bytes, auxOff + 12)
                    }
                }
            }

            val parsedSym = CoffSymbol(
                slot = slot,
                name = symName,
                value = value,
                secNum = secNum,
                type = type,
                storageClass = storageClass,
                auxCount = auxCount,
                checkSum = checkSum,
                assocSec = assocSec,
                selection = selection
            )
            symBySlot[slot] = parsedSym

            if (storageClass != IMAGE_SYM_CLASS_FILE && secNum != -2) {
                if (secNum == -1 && symName == "@feat.00") {
                    feat00Symbol = parsedSym
                } else if (isSecDef) {
                    if (secDefBySection[secNum] == null) {
                        secDefBySection[secNum] = parsedSym
                    }
                } else if (secNum in 1..numSections) {
                    symbolsBySection[secNum].add(parsedSym)
                }
            }

            slot += 1 + auxCount
        }

        val keepSection = BooleanArray(numSections + 1)
        for (sIdx in 1..numSections) {
            val sec = sections[sIdx]
            val isDebugOrDirective = sec.name == ".drectve" ||
                sec.name == ".llvm_addrsig" ||
                sec.name.startsWith(".debug")
            if (isDebugOrDirective) continue

            val isEmptyUnnamed = sec.sizeOfRawData == 0 &&
                sec.relocs.isEmpty() &&
                (sec.characteristics and IMAGE_SCN_CNT_UNINITIALIZED_DATA) == 0 &&
                symbolsBySection[sIdx].isEmpty()
            if (isEmptyUnnamed) continue

            keepSection[sIdx] = true
        }

        val parent = IntArray(numSections + 1) { it }
        val rank = IntArray(numSections + 1)

        fun find(x: Int): Int {
            var cur = x
            while (parent[cur] != cur) {
                parent[cur] = parent[parent[cur]]
                cur = parent[cur]
            }
            return cur
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra == rb) return
            if (rank[ra] < rank[rb]) {
                parent[ra] = rb
            } else if (rank[ra] > rank[rb]) {
                parent[rb] = ra
            } else {
                parent[rb] = ra
                rank[ra]++
            }
        }

        for (sIdx in 1..numSections) {
            if (!keepSection[sIdx]) continue
            val sec = sections[sIdx]
            val secDef = secDefBySection[sIdx]

            if (secDef != null &&
                secDef.selection == IMAGE_COMDAT_SELECT_ASSOCIATIVE &&
                secDef.assocSec in 1..numSections &&
                keepSection[secDef.assocSec]
            ) {
                union(sIdx, secDef.assocSec)
            }

            val isUnwind = sec.name.startsWith(".pdata") || sec.name.startsWith(".xdata")
            for (rel in sec.relocs) {
                val targetSym = symBySlot.getOrNull(rel.symSlot) ?: continue
                val targetSec = targetSym.secNum
                if (targetSec in 1..numSections && keepSection[targetSec]) {
                    if (isUnwind || targetSym.storageClass != IMAGE_SYM_CLASS_EXTERNAL) {
                        union(sIdx, targetSec)
                    }
                }
            }
        }

        val componentSections = linkedMapOf<Int, MutableList<Int>>()
        val componentHasExternal = HashMap<Int, Boolean>()
        for (sIdx in 1..numSections) {
            if (!keepSection[sIdx]) continue
            val root = find(sIdx)
            componentSections.getOrPut(root) { mutableListOf() }.add(sIdx)
            if (componentHasExternal[root] != true) {
                if (symbolsBySection[sIdx].any { it.storageClass == IMAGE_SYM_CLASS_EXTERNAL }) {
                    componentHasExternal[root] = true
                }
            }
        }

        val emittedMembers = ArrayList<ByteArray>(componentSections.size)
        for ((root, secIndices) in componentSections) {
            if (componentHasExternal[root] != true) continue
            emittedMembers.add(
                emitStandardCoffObject(
                    actualMachine = actualMachine,
                    componentSections = secIndices,
                    sections = sections,
                    symBySlot = symBySlot,
                    secDefBySection = secDefBySection,
                    symbolsBySection = symbolsBySection,
                    feat00Symbol = feat00Symbol
                )
            )
        }
        return emittedMembers
    }

    private data class OutSymbol(
        val nameBytes: ByteArray,
        val value: Int,
        val secNum: Int, // 1-based section index, 0=UNDEF, -1=ABS
        val type: Int,
        val storageClass: Int,
        val auxCount: Int,
        val auxLength: Int = 0,
        val auxNumRelocs: Int = 0,
        val auxCheckSum: Int = 0,
        val auxNumber: Int = 0,
        val auxSelection: Int = 0
    )

    private fun emitStandardCoffObject(
        actualMachine: Int,
        componentSections: List<Int>,
        sections: List<CoffSection>,
        symBySlot: Array<CoffSymbol?>,
        secDefBySection: Array<CoffSymbol?>,
        symbolsBySection: Array<MutableList<CoffSymbol>>,
        feat00Symbol: CoffSymbol?
    ): ByteArray {
        check(componentSections.size <= 0xFFFE) {
            "Component exceeds standard COFF 16-bit section limit: ${componentSections.size}"
        }

        val oldToNewSec = HashMap<Int, Int>(componentSections.size * 2)
        componentSections.forEachIndexed { idx, oldSec ->
            oldToNewSec[oldSec] = idx + 1
        }

        val outSymbols = ArrayList<OutSymbol>(componentSections.size * 4)
        val oldSlotToNewSlot = HashMap<Int, Int>(componentSections.size * 4)
        var nextSlot = 0

        if (feat00Symbol != null) {
            val featBytes = feat00Symbol.name.toByteArray(Charsets.UTF_8)
            oldSlotToNewSlot[feat00Symbol.slot] = nextSlot
            outSymbols.add(
                OutSymbol(
                    nameBytes = featBytes,
                    value = feat00Symbol.value,
                    secNum = -1,
                    type = feat00Symbol.type,
                    storageClass = feat00Symbol.storageClass,
                    auxCount = 0
                )
            )
            nextSlot += 1
        }

        for (oldSec in componentSections) {
            val sec = sections[oldSec]
            val newSec = oldToNewSec.getValue(oldSec)
            val origSecDef = secDefBySection[oldSec]
            val assocNewSec = if (origSecDef != null && origSecDef.selection == IMAGE_COMDAT_SELECT_ASSOCIATIVE) {
                oldToNewSec[origSecDef.assocSec] ?: 0
            } else {
                0
            }
            val secNameBytes = sec.name.toByteArray(Charsets.UTF_8)
            if (origSecDef != null) {
                oldSlotToNewSlot[origSecDef.slot] = nextSlot
            }
            outSymbols.add(
                OutSymbol(
                    nameBytes = secNameBytes,
                    value = 0,
                    secNum = newSec,
                    type = 0,
                    storageClass = IMAGE_SYM_CLASS_STATIC,
                    auxCount = 1,
                    auxLength = sec.sizeOfRawData,
                    auxNumRelocs = sec.relocs.size.coerceAtMost(0xFFFF),
                    auxCheckSum = origSecDef?.checkSum ?: 0,
                    auxNumber = assocNewSec,
                    auxSelection = origSecDef?.selection ?: 0
                )
            )
            nextSlot += 2
        }

        for (oldSec in componentSections) {
            val newSec = oldToNewSec.getValue(oldSec)
            for (sym in symbolsBySection[oldSec]) {
                if (oldSlotToNewSlot.containsKey(sym.slot)) continue
                oldSlotToNewSlot[sym.slot] = nextSlot
                outSymbols.add(
                    OutSymbol(
                        nameBytes = sym.name.toByteArray(Charsets.UTF_8),
                        value = sym.value,
                        secNum = newSec,
                        type = sym.type,
                        storageClass = sym.storageClass,
                        auxCount = 0
                    )
                )
                nextSlot += 1
            }
        }

        val undefSlotByName = HashMap<String, Int>()
        val absSlotByKey = HashMap<String, Int>()
        for (oldSec in componentSections) {
            val sec = sections[oldSec]
            for (rel in sec.relocs) {
                if (oldSlotToNewSlot.containsKey(rel.symSlot)) continue
                val origSym = symBySlot.getOrNull(rel.symSlot)
                    ?: error("Relocation references missing symbol slot ${rel.symSlot} in section ${sec.name}")
                if (origSym.secNum == -1) {
                    val absKey = "${origSym.name}|${origSym.value}|${origSym.storageClass}"
                    val mapped = absSlotByKey.getOrPut(absKey) {
                        val allocated = nextSlot
                        outSymbols.add(
                            OutSymbol(
                                nameBytes = origSym.name.toByteArray(Charsets.UTF_8),
                                value = origSym.value,
                                secNum = -1,
                                type = origSym.type,
                                storageClass = origSym.storageClass,
                                auxCount = 0
                            )
                        )
                        nextSlot += 1
                        allocated
                    }
                    oldSlotToNewSlot[rel.symSlot] = mapped
                } else {
                    val mapped = undefSlotByName.getOrPut(origSym.name) {
                        val allocated = nextSlot
                        outSymbols.add(
                            OutSymbol(
                                nameBytes = origSym.name.toByteArray(Charsets.UTF_8),
                                value = 0,
                                secNum = 0,
                                type = origSym.type,
                                storageClass = IMAGE_SYM_CLASS_EXTERNAL,
                                auxCount = 0
                            )
                        )
                        nextSlot += 1
                        allocated
                    }
                    oldSlotToNewSlot[rel.symSlot] = mapped
                }
            }
        }

        val strTable = ByteArrayOutputStream(256)
        // Reserve 4 bytes at offset 0 for string table total size.
        strTable.write(0)
        strTable.write(0)
        strTable.write(0)
        strTable.write(0)

        fun internString(bytes: ByteArray): Int {
            val off = strTable.size()
            strTable.write(bytes)
            strTable.write(0)
            return off
        }

        val sectionHeaderNames = ArrayList<ByteArray>(componentSections.size)
        for (oldSec in componentSections) {
            val secNameBytes = sections[oldSec].name.toByteArray(Charsets.UTF_8)
            val hdrName = ByteArray(8)
            if (secNameBytes.size <= 8 && (secNameBytes.isEmpty() || secNameBytes[0] != '/'.code.toByte())) {
                secNameBytes.copyInto(hdrName, 0)
            } else {
                val off = internString(secNameBytes)
                val slashBytes = "/$off".toByteArray(Charsets.US_ASCII)
                check(slashBytes.size <= 8) {
                    "COFF section name string table offset overflow: /$off"
                }
                slashBytes.copyInto(hdrName, 0)
            }
            sectionHeaderNames.add(hdrName)
        }

        val symbolHeaderNames = ArrayList<ByteArray>(outSymbols.size)
        for (sym in outSymbols) {
            val nameField = ByteArray(8)
            if (sym.nameBytes.size <= 8) {
                sym.nameBytes.copyInto(nameField, 0)
            } else {
                val off = internString(sym.nameBytes)
                writeU32LE(nameField, 4, off)
            }
            symbolHeaderNames.add(nameField)
        }

        val strTableBytes = strTable.toByteArray()
        writeU32LE(strTableBytes, 0, strTableBytes.size)

        val numSec = componentSections.size
        val headerSize = 20 + numSec * 40
        var cursor = headerSize

        val ptrRawDataOut = IntArray(numSec)
        val ptrRelocsOut = IntArray(numSec)
        val numRelocsU16Out = IntArray(numSec)
        val characteristicsOut = IntArray(numSec)
        val hasOvflOut = BooleanArray(numSec)

        for (i in componentSections.indices) {
            val sec = sections[componentSections[i]]
            val isBss = (sec.characteristics and IMAGE_SCN_CNT_UNINITIALIZED_DATA) != 0
            if (!isBss && sec.sizeOfRawData > 0 && sec.rawData.isNotEmpty()) {
                ptrRawDataOut[i] = cursor
                cursor += sec.rawData.size
            } else {
                ptrRawDataOut[i] = 0
            }

            if (sec.relocs.isNotEmpty()) {
                ptrRelocsOut[i] = cursor
                if (sec.relocs.size >= 0xFFFF) {
                    hasOvflOut[i] = true
                    numRelocsU16Out[i] = 0xFFFF
                    characteristicsOut[i] = sec.characteristics or IMAGE_SCN_LNK_NRELOC_OVFL
                    cursor += (sec.relocs.size + 1) * 10
                } else {
                    hasOvflOut[i] = false
                    numRelocsU16Out[i] = sec.relocs.size
                    characteristicsOut[i] = sec.characteristics and IMAGE_SCN_LNK_NRELOC_OVFL.inv()
                    cursor += sec.relocs.size * 10
                }
            } else {
                ptrRelocsOut[i] = 0
                numRelocsU16Out[i] = 0
                characteristicsOut[i] = sec.characteristics and IMAGE_SCN_LNK_NRELOC_OVFL.inv()
            }
        }

        val symTableStart = cursor
        val symTableSize = nextSlot * 18
        val totalSize = symTableStart + symTableSize + strTableBytes.size
        val out = ByteArray(totalSize)

        // 20-byte Standard COFF Header
        writeU16LE(out, 0, actualMachine)
        writeU16LE(out, 2, numSec)
        writeU32LE(out, 4, 0) // TimeDateStamp
        writeU32LE(out, 8, symTableStart)
        writeU32LE(out, 12, nextSlot)
        writeU16LE(out, 16, 0) // SizeOfOptionalHeader
        writeU16LE(out, 18, 0) // Characteristics

        // 40-byte Section Headers
        for (i in componentSections.indices) {
            val sec = sections[componentSections[i]]
            val shOff = 20 + i * 40
            sectionHeaderNames[i].copyInto(out, shOff)
            writeU32LE(out, shOff + 8, 0) // VirtualSize (0 in object files)
            writeU32LE(out, shOff + 12, 0) // VirtualAddress
            writeU32LE(out, shOff + 16, sec.sizeOfRawData)
            writeU32LE(out, shOff + 20, ptrRawDataOut[i])
            writeU32LE(out, shOff + 24, ptrRelocsOut[i])
            writeU32LE(out, shOff + 28, 0) // PointerToLinenumbers
            writeU16LE(out, shOff + 32, numRelocsU16Out[i])
            writeU16LE(out, shOff + 34, 0) // NumberOfLinenumbers
            writeU32LE(out, shOff + 36, characteristicsOut[i])
        }

        // Section Raw Data + Relocations
        for (i in componentSections.indices) {
            val sec = sections[componentSections[i]]
            if (ptrRawDataOut[i] != 0 && sec.rawData.isNotEmpty()) {
                sec.rawData.copyInto(out, ptrRawDataOut[i])
            }
            if (ptrRelocsOut[i] != 0 && sec.relocs.isNotEmpty()) {
                var relPos = ptrRelocsOut[i]
                if (hasOvflOut[i]) {
                    writeU32LE(out, relPos, sec.relocs.size + 1)
                    writeU32LE(out, relPos + 4, 0)
                    writeU16LE(out, relPos + 8, 0)
                    relPos += 10
                }
                for (rel in sec.relocs) {
                    writeU32LE(out, relPos, rel.rva)
                    writeU32LE(out, relPos + 4, oldSlotToNewSlot.getValue(rel.symSlot))
                    writeU16LE(out, relPos + 8, rel.type)
                    relPos += 10
                }
            }
        }

        // 18-byte Symbol Records + Aux Section Definitions
        var symPos = symTableStart
        for (i in outSymbols.indices) {
            val sym = outSymbols[i]
            symbolHeaderNames[i].copyInto(out, symPos)
            writeU32LE(out, symPos + 8, sym.value)
            writeI16LE(out, symPos + 12, sym.secNum)
            writeU16LE(out, symPos + 14, sym.type)
            out[symPos + 16] = (sym.storageClass and 0xFF).toByte()
            out[symPos + 17] = (sym.auxCount and 0xFF).toByte()
            symPos += 18

            if (sym.auxCount == 1) {
                writeU32LE(out, symPos, sym.auxLength)
                writeU16LE(out, symPos + 4, sym.auxNumRelocs)
                writeU16LE(out, symPos + 6, 0)
                writeU32LE(out, symPos + 8, sym.auxCheckSum)
                writeU16LE(out, symPos + 12, sym.auxNumber)
                out[symPos + 14] = (sym.auxSelection and 0xFF).toByte()
                out[symPos + 15] = 0
                out[symPos + 16] = 0
                out[symPos + 17] = 0
                symPos += 18
            }
        }
        check(symPos == symTableStart + symTableSize) {
            "COFF symbol table write mismatch: $symPos != ${symTableStart + symTableSize}"
        }

        strTableBytes.copyInto(out, symTableStart + symTableSize)
        return out
    }

    private fun decodeSectionName(bytes: ByteArray, secOff: Int, stringTableOffset: Int): String {
        val raw = readFixedCString(bytes, secOff, 8)
        if (!raw.startsWith("/")) return raw
        val decOff = raw.substring(1).trim { it == '\u0000' || it == ' ' }.toIntOrNull() ?: return raw
        return readNullTerminatedString(bytes, stringTableOffset + decOff)
    }

    private fun readU16LE(bytes: ByteArray, off: Int): Int =
        (bytes[off].toInt() and 0xFF) or ((bytes[off + 1].toInt() and 0xFF) shl 8)

    private fun readI16LE(bytes: ByteArray, off: Int): Int {
        val v = readU16LE(bytes, off)
        return if ((v and 0x8000) != 0) v or -0x10000 else v
    }

    private fun readU32LE(bytes: ByteArray, off: Int): Int =
        (bytes[off].toInt() and 0xFF) or
            ((bytes[off + 1].toInt() and 0xFF) shl 8) or
            ((bytes[off + 2].toInt() and 0xFF) shl 16) or
            ((bytes[off + 3].toInt() and 0xFF) shl 24)

    private fun writeU16LE(bytes: ByteArray, off: Int, v: Int) {
        bytes[off] = (v and 0xFF).toByte()
        bytes[off + 1] = ((v ushr 8) and 0xFF).toByte()
    }

    private fun writeI16LE(bytes: ByteArray, off: Int, v: Int) = writeU16LE(bytes, off, v)

    private fun writeU32LE(bytes: ByteArray, off: Int, v: Int) {
        bytes[off] = (v and 0xFF).toByte()
        bytes[off + 1] = ((v ushr 8) and 0xFF).toByte()
        bytes[off + 2] = ((v ushr 16) and 0xFF).toByte()
        bytes[off + 3] = ((v ushr 24) and 0xFF).toByte()
    }

    private fun readFixedCString(bytes: ByteArray, off: Int, maxLen: Int): String {
        var len = 0
        while (len < maxLen && off + len < bytes.size && bytes[off + len] != 0.toByte()) {
            len++
        }
        return String(bytes, off, len, Charsets.UTF_8)
    }

    private fun readNullTerminatedString(bytes: ByteArray, off: Int): String {
        if (off < 0 || off >= bytes.size) return ""
        var end = off
        while (end < bytes.size && bytes[end] != 0.toByte()) {
            end++
        }
        return String(bytes, off, end - off, Charsets.UTF_8)
    }
}
