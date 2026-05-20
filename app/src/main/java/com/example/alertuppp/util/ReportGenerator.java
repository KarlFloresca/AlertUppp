package com.example.alertuppp.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.os.Environment;
import android.widget.Toast;

import com.example.alertuppp.model.EvacuationCenter;
import com.example.alertuppp.model.Family;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportGenerator {

    public static void generateCenterReport(Context context, List<EvacuationCenter> centers, List<Family> families) {
        PdfDocument document = new PdfDocument();
        
        // Page 1: Summary
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create(); // A4 size in points
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();
        
        int y = 50;
        
        // Header
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(24);
        paint.setColor(Color.BLACK);
        canvas.drawText("AlertUppp - Evacuation Report", 50, y, paint);
        
        y += 30;
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(12);
        String date = new SimpleDateFormat("MMMM dd, yyyy HH:mm", Locale.getDefault()).format(new Date());
        canvas.drawText("Generated on: " + date, 50, y, paint);
        
        y += 40;
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(16);
        canvas.drawText("1. Center Occupancy Summary", 50, y, paint);
        
        y += 30;
        paint.setTextSize(12);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("Center Name", 50, y, paint);
        canvas.drawText("Families", 320, y, paint);
        canvas.drawText("Occupancy", 410, y, paint);
        canvas.drawText("Status", 510, y, paint);
        
        y += 10;
        canvas.drawLine(50, y, 565, y, paint);
        
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        int totalOccupancy = 0;
        for (EvacuationCenter c : centers) {
            y += 25;
            
            // Count families for this center
            int familyCount = 0;
            if (families != null) {
                for (Family f : families) {
                    if (c.getId().equals(f.getCenterId())) familyCount++;
                }
            }

            canvas.drawText(c.getName(), 50, y, paint);
            canvas.drawText(String.valueOf(familyCount), 320, y, paint);
            canvas.drawText(c.getCurrentOccupancy() + " / " + c.getMaxCapacity(), 410, y, paint);
            canvas.drawText(c.getStatus().toUpperCase(), 510, y, paint);
            totalOccupancy += c.getCurrentOccupancy();
            
            if (y > 750) {
                document.finishPage(page);
                page = document.startPage(new PdfDocument.PageInfo.Builder(595, 842, document.getPages().size() + 1).create());
                canvas = page.getCanvas();
                y = 50;
            }
        }
        
        y += 30;
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("Total Families: " + (families != null ? families.size() : 0), 50, y, paint);
        y += 20;
        canvas.drawText("Total Evacuees: " + totalOccupancy, 50, y, paint);
        
        y += 50;
        canvas.drawText("2. Family Distribution", 50, y, paint);
        
        y += 30;
        paint.setTextSize(11);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("Family Name", 50, y, paint);
        canvas.drawText("Members", 230, y, paint);
        canvas.drawText("Location", 320, y, paint);
        
        y += 10;
        canvas.drawLine(50, y, 565, y, paint);
        
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        for (Family f : families) {
            y += 20;
            String fName = f.getFamilyName() != null ? f.getFamilyName() : "Family";
            String cName = f.getCenterName() != null ? f.getCenterName() : "Unknown";
            String mCount = String.valueOf(f.getMemberCount());
            
            canvas.drawText(fName, 50, y, paint);
            canvas.drawText(mCount, 230, y, paint);
            canvas.drawText(cName, 320, y, paint);
            
            if (y > 800) {
                document.finishPage(page);
                page = document.startPage(new PdfDocument.PageInfo.Builder(595, 842, document.getPages().size() + 1).create());
                canvas = page.getCanvas();
                y = 50;
            }
        }
        
        document.finishPage(page);
        
        // Save the document
        String fileName = "AlertUppp_Report_" + System.currentTimeMillis() + ".pdf";
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(downloads, fileName);
        
        try {
            document.writeTo(new FileOutputStream(file));
            Toast.makeText(context, "Report saved to Downloads", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Error saving PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            document.close();
        }
    }
}
