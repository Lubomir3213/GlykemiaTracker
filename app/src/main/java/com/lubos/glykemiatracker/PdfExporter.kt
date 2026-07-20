package com.lubos.glykemiatracker

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Objekt zodpovedný za generovanie PDF reportov a ich zdieľanie.
 * Obsahuje logiku pre kreslenie grafov a tabuliek priamo na PDF plátno.
 */
object PdfExporter {
    
    /**
     * Preformátuje technický názov kategórie na čitateľnú slovenčinu pre PDF.
     */
    private fun formatKateg(k: String): String {
        return k.replace("predRanajkami", "pred raňajkami")
            .replace("poRanajkach", "po raňajkách")
            .replace("predObedom", "pred obedom")
            .replace("poObede", "po obede")
            .replace("predVecerou", "pred večerou")
            .replace("poVeceri", "po večeri")
            .replace("Noc", "v noci")
            .replace("Raňajky Pred", "pred raňajkami")
            .replace("Raňajky Po", "po raňajkách")
            .replace("Obed Pred", "pred obedom")
            .replace("Obed Po", "po obede")
            .replace("Večera Pred", "pred večerou")
            .replace("Večera Po", "po večeri")
    }

    /**
     * Generuje PDF s grafom trendu glykémie.
     */
    fun generujPosliPdfGraf(context: Context, merania: List<Map<String, Any>>) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create() // Krajinka A4
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint().apply { isAntiAlias = true }
        
        canvas.drawText("Graf trendu glykémie", 50f, 40f, paint.apply { textSize = 20f; isFakeBoldText = true })
        
        // Nastavenie osí grafu
        val left = 60f; val top = 80f; val right = 780f; val bottom = 500f; val gW = right - left; val gH = bottom - top
        paint.strokeWidth = 2f; paint.color = android.graphics.Color.BLACK
        canvas.drawLine(left, bottom, right, bottom, paint)
        canvas.drawLine(left, top, left, bottom, paint)
        
        if (merania.size > 1) {
            val h = merania.map { it["hodnota"].toString().toFloatOrNull() ?: 5f }
            val maxV = (h.maxOrNull() ?: 10f) + 1f
            val minV = ((h.minOrNull() ?: 4f) - 1f).coerceAtLeast(0f)
            val range = maxV - minV
            
            paint.textSize = 12f
            canvas.drawText(String.format(Locale.US, "%.1f", maxV), 20f, top + 10f, paint)
            canvas.drawText(String.format(Locale.US, "%.1f", minV), 20f, bottom, paint)
            
            // Kreslenie čiary trendu a bodov
            val path = android.graphics.Path()
            val linePaint = Paint().apply { color = android.graphics.Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 1f; isAntiAlias = true }
            val format = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
            
            merania.forEachIndexed { i, m ->
                val hodn = m["hodnota"].toString()
                val valH = hodn.toFloatOrNull() ?: 5f
                val x = left + i * (gW / (merania.size - 1))
                val y = bottom - ((valH - minV) / range * gH)
                
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                
                val dotPaint = Paint().apply { color = getGlykemiaColorInt(hodn); style = Paint.Style.FILL }
                canvas.drawCircle(x, y, 5f, dotPaint)
                
                // Popisky dátumov pod uhlom
                if (i % (if (merania.size > 8) merania.size / 6 else 1) == 0 || i == merania.size - 1) {
                    val ts = m["datum_cas"] as? com.google.firebase.Timestamp; val date = ts?.toDate()
                    if (date != null) { 
                        canvas.save()
                        canvas.rotate(45f, x, bottom + 20f)
                        canvas.drawText(format.format(date), x, bottom + 20f, paint.apply { textSize = 9f })
                        canvas.restore() 
                    }
                }
            }
            canvas.drawPath(path, linePaint)
        }
        pdfDocument.finishPage(page)
        ulozAZdielajPdf(context, pdfDocument)
    }

    /**
     * Generuje sumárne PDF (Tabuľka 1) so zarovnaním na desatinnú čiarku.
     */
    fun exportovatAOdoslatPDF(context: Context, data: List<Zaznam>, titul: String) {
        val pdfDocument = PdfDocument()
        val textPaint = Paint().apply { textSize = 9f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val boldPaint = Paint().apply { textSize = 9f; isFakeBoldText = true; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true; isAntiAlias = true }
        val linePaint = Paint().apply { strokeWidth = 0.5f; style = Paint.Style.STROKE; isAntiAlias = true }
        val boldLinePaint = Paint().apply { strokeWidth = 1.2f; style = Paint.Style.STROKE; isAntiAlias = true }
        
        val xL = 40f; val stX = listOf(40f, 115f, 170f, 225f, 280f, 335f, 390f, 445f, 500f); val xR = 500f
        var pNum = 1; var pInfo = PdfDocument.PageInfo.Builder(595, 842, pNum).create(); var page = pdfDocument.startPage(pInfo); var canvas = page.canvas; var y = 120f
        
        fun drawH(can: Canvas, t: String) { 
            titlePaint.textAlign = Paint.Align.LEFT
            can.drawText(t, 40f, 50f, titlePaint)
            can.drawRect(xL, 80f, xR, 120f, boldLinePaint)
            listOf(stX[1], stX[3], stX[5], stX[7]).forEach { x -> can.drawLine(x, 80f, x, 120f, boldLinePaint) }
            boldPaint.textAlign = Paint.Align.CENTER
            can.drawText("Dátum", (stX[0]+stX[1])/2, 105f, boldPaint)
            can.drawText("Raňajky", (stX[1]+stX[3])/2, 95f, boldPaint)
            can.drawText("Obed", (stX[3]+stX[5])/2, 95f, boldPaint)
            can.drawText("Večera", (stX[5]+stX[7])/2, 95f, boldPaint)
            can.drawText("Noc", (stX[7]+stX[8])/2, 105f, boldPaint)
            can.drawLine(stX[1], 100f, stX[7], 100f, linePaint)
            textPaint.textAlign = Paint.Align.CENTER
            for(i in 1..6) { 
                val label = if(i%2 != 0) "Pred" else "Po"
                can.drawText(label, (stX[i]+stX[i+1])/2, 115f, textPaint)
                if(i%2 != 0) can.drawLine(stX[i+1], 100f, stX[i+1], 120f, linePaint) 
            } 
        }
        
        drawH(canvas, titul)
        data.forEach { r ->
            val vals = listOf(r.ranajkyPred, r.ranajkyPo, r.obedPred, r.obedPo, r.veceraPred, r.veceraPo, r.noc)
            val maxC = vals.maxOfOrNull { it.size } ?: 1; val rH = (maxC * 12f) + 10f
            
            if (y + rH > 780f) { 
                pdfDocument.finishPage(page); pNum++; pInfo = PdfDocument.PageInfo.Builder(595, 842, pNum).create()
                page = pdfDocument.startPage(pInfo); canvas = page.canvas; drawH(canvas, titul); y = 120f 
            }
            
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(r.datum, (stX[0]+stX[1])/2, y + (rH/2) + 4f, textPaint)
            
            vals.forEachIndexed { i, list -> 
                list.forEachIndexed { idx, vPair -> 
                    val valStr = vPair.first.replace(".", ",")
                    val color = getGlykemiaColorInt(valStr)
                    textPaint.color = if (color == android.graphics.Color.WHITE || color == android.graphics.Color.TRANSPARENT) android.graphics.Color.BLACK else color
                    
                    val stredStlpca = (stX[i+1] + stX[i+2]) / 2
                    val casti = valStr.split(",")
                    val predCiarkou = casti[0]
                    val poCiarkou = if (casti.size > 1) "," + casti[1] else ""
                    
                    textPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(predCiarkou, stredStlpca, y + 12f + (idx * 12f), textPaint)
                    textPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(poCiarkou, stredStlpca, y + 12f + (idx * 12f), textPaint)
                    
                    textPaint.color = android.graphics.Color.BLACK 
                } 
            }
            canvas.drawLine(xL, y + rH, xR, y + rH, linePaint)
            stX.forEachIndexed { i, x -> 
                val p = if(i==0 || i==1 || i==3 || i==5 || i==7 || i==8) boldLinePaint else linePaint
                canvas.drawLine(x, y, x, y + rH, p) 
            }
            y += rH
        }
        pdfDocument.finishPage(page)
        ulozAZdielajPdf(context, pdfDocument)
    }

    /**
     * Generuje detailné PDF (Tabuľka 2) so slovenskými kategóriami a farebnými hodnotami.
     */
    fun exportovatDetailPDF(context: Context, data: List<Zaznam>) {
        val pdfDocument = PdfDocument()
        val textP = Paint().apply { textSize = 8f; isAntiAlias = true }
        val boldP = Paint().apply { textSize = 8f; isFakeBoldText = true; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val titleP = Paint().apply { textSize = 16f; isFakeBoldText = true; isAntiAlias = true }
        val lineP = Paint().apply { strokeWidth = 0.5f; style = Paint.Style.STROKE }
        val boldLP = Paint().apply { strokeWidth = 1.2f; style = Paint.Style.STROKE }
        
        val stX = listOf(40f, 130f, 170f, 240f, 290f, 555f); val xR = 555f
        var pNum = 1; var pInfo = PdfDocument.PageInfo.Builder(595, 842, pNum).create(); var page = pdfDocument.startPage(pInfo); var canvas = page.canvas; var y = 100f
        
        val prefs = context.getSharedPreferences("glykemia_prefs", Context.MODE_PRIVATE)
        val isIzolovany = prefs.getBoolean("izolovany", true)

        fun drawH() { 
            titleP.textAlign = Paint.Align.LEFT
            canvas.drawText("Detailný zoznam meraní", 40f, 50f, titleP); canvas.drawRect(40f, 80f, xR, 100f, boldLP)
            val h = listOf("Dátum a čas", if(isIzolovany) "Osoba" else "Mob", "Kategória", "Hodn", "Poznámka")
            boldP.textAlign = Paint.Align.CENTER
            h.forEachIndexed { i, s -> canvas.drawText(s, (stX[i]+stX[i+1])/2, 93f, boldP) }
            stX.forEachIndexed { i, x -> 
                val p = if(i == 0 || i == 1 || i == 5) boldLP else lineP
                canvas.drawLine(x, 80f, x, 100f, p) 
            } 
        }
        
        drawH()
        data.forEach { r ->
            val hodn = r.ranajkyPred.firstOrNull()?.first ?: ""
            val kateg = r.ranajkyPo.firstOrNull()?.second ?: ""
            val mobil = r.obedPred.firstOrNull()?.second ?: ""
            val poznamka = r.obedPo.firstOrNull()?.second ?: ""
            
            val displayMob = if (isIzolovany) {
                when(mobil) {
                    "Mobil 1" -> prefs.getString("meno_1", "").let { if(it.isNullOrEmpty()) "O1" else it.take(2) }
                    "Mobil 2" -> prefs.getString("meno_2", "").let { if(it.isNullOrEmpty()) "O2" else it.take(2) }
                    "Mobil 3" -> prefs.getString("meno_3", "").let { if(it.isNullOrEmpty()) "O3" else it.take(2) }
                    else -> mobil
                }
            } else { mobil.replace("Mobil ", "Mob ") }

            // Zalomenie dlhej poznámky do viacerých riadkov
            val lines = mutableListOf<String>(); var cur = ""
            poznamka.split(" ").forEach { w -> 
                if(textP.measureText("$cur $w") < (stX[5]-stX[4]-10)) cur += "$w " else { lines.add(cur); cur = "$w " } 
            }; lines.add(cur)
            
            val rH = (lines.size * 10f) + 10f
            if (y + rH > 780f) { 
                pdfDocument.finishPage(page); pNum++; pInfo = PdfDocument.PageInfo.Builder(595, 842, pNum).create()
                page = pdfDocument.startPage(pInfo); canvas = page.canvas; drawH(); y = 100f 
            }
            
            textP.textAlign = Paint.Align.LEFT
            canvas.drawText(r.datum, stX[0]+5, y+12, textP)
            canvas.drawText(displayMob, stX[1]+5, y+12, textP)
            canvas.drawText(formatKateg(kateg), stX[2]+5, y+12, textP)
            
            val color = getGlykemiaColorInt(hodn)
            boldP.color = if (color == android.graphics.Color.WHITE || color == android.graphics.Color.TRANSPARENT) android.graphics.Color.BLACK else color
            
            val stredStlpcaHodn = (stX[3] + stX[4]) / 2
            val hFormatted = hodn.replace(".", ",")
            val hCasti = hFormatted.split(",")
            val hPred = hCasti[0]
            val hPo = if (hCasti.size > 1) "," + hCasti[1] else ""

            boldP.textAlign = Paint.Align.RIGHT
            canvas.drawText(hPred, stredStlpcaHodn, y + 12, boldP)
            boldP.textAlign = Paint.Align.LEFT
            canvas.drawText(hPo, stredStlpcaHodn, y + 12, boldP)
            boldP.color = android.graphics.Color.BLACK 

            textP.textAlign = Paint.Align.LEFT
            lines.forEachIndexed { i, l -> canvas.drawText(l, stX[4]+5, y+12+(i*10), textP) }
            canvas.drawLine(40f, y+rH, xR, y+rH, lineP)
            stX.forEachIndexed { i, x -> 
                val p = if (i == 0 || i == 1 || i == 5) boldLP else lineP
                canvas.drawLine(x, y, x, y+rH, p) 
            }
            y += rH
        }
        pdfDocument.finishPage(page)
        ulozAZdielajPdf(context, pdfDocument)
    }

    /**
     * Uloží vygenerované PDF do cache a otvorí systémový dialóg na zdieľanie/otvorenie.
     */
    private fun ulozAZdielajPdf(context: Context, pdfDocument: PdfDocument) {
        val file = File(context.cacheDir, "GlykemiaReport.pdf")
        try {
            val fos = FileOutputStream(file); pdfDocument.writeTo(fos); pdfDocument.close(); fos.close()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/pdf"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val shareIntent = Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); clipData = android.content.ClipData.newRawUri("", uri) }
            val chooser = Intent.createChooser(viewIntent, "Otvoriť alebo Odoslať PDF..."); chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(shareIntent)); context.startActivity(chooser)
        } catch (e: Exception) { e.printStackTrace() }
    }
}
