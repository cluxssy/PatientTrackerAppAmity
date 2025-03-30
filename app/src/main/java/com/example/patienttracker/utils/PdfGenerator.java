package com.example.patienttracker.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.example.patienttracker.models.MedicalRecord;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

/**
 * Utility class for generating PDF documents for medical records
 */
public class PdfGenerator {

    private static final String TAG = "PdfGenerator";

    // Font definitions
    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BaseColor.BLACK);
    private static final Font SUBTITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BaseColor.DARK_GRAY);
    private static final Font SECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
    private static final Font NORMAL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.BLACK);
    private static final Font SMALL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8, BaseColor.GRAY);

    /**
     * Generate a PDF for a medical record
     *
     * @param context Context for file operations
     * @param record The medical record to convert to PDF
     * @return The generated PDF file
     */
    public static File generateMedicalRecordPdf(Context context, MedicalRecord record) {
        if (context == null || record == null) {
            return null;
        }

        // Create a new document
        Document document = new Document(PageSize.A4);

        try {
            // Generate filename based on patient and date
            String fileName = "MedicalRecord_" +
                    record.getPatientName().replaceAll("\\s+", "_") + "_" +
                    DateTimeUtils.formatForFileName(new Date(record.getDate())) + ".pdf";

            // Create the output file
            File pdfFolder = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "PatientTracker");
            if (!pdfFolder.exists()) {
                pdfFolder.mkdirs();
            }

            File pdfFile = new File(pdfFolder, fileName);

            // Initialize PDF writer
            PdfWriter.getInstance(document, new FileOutputStream(pdfFile));

            // Open the document
            document.open();

            // Add content
            addContent(document, record);

            // Close the document
            document.close();

            return pdfFile;

        } catch (DocumentException | IOException e) {
            Log.e(TAG, "Error creating PDF: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Add content to the PDF document
     */
    private static void addContent(Document document, MedicalRecord record) throws DocumentException {
        // Add title
        Paragraph title = new Paragraph("Medical Record", TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        // Add spacing
        document.add(new Paragraph(" "));

        // Add patient and date information
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);

        // Format date
        String formattedDate = DateTimeUtils.formatDate(new Date(record.getDate()));

        // Add patient name
        PdfPCell patientCell = new PdfPCell(new Phrase("Patient: " + record.getPatientName(), SECTION_FONT));
        patientCell.setBorder(0);
        infoTable.addCell(patientCell);

        // Add date
        PdfPCell dateCell = new PdfPCell(new Phrase("Date: " + formattedDate, SECTION_FONT));
        dateCell.setBorder(0);
        dateCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        infoTable.addCell(dateCell);

        // Add doctor name
        PdfPCell doctorCell = new PdfPCell(new Phrase("Doctor: Dr. " + record.getDoctorName(), SECTION_FONT));
        doctorCell.setBorder(0);
        doctorCell.setColspan(2);
        infoTable.addCell(doctorCell);

        document.add(infoTable);

        // Add spacing
        document.add(new Paragraph(" "));

        // Add divider
        Paragraph divider = new Paragraph("-----------------------------------------------------");
        divider.setAlignment(Element.ALIGN_CENTER);
        document.add(divider);

        // Add spacing
        document.add(new Paragraph(" "));

        // Add diagnosis section
        Paragraph diagnosisTitle = new Paragraph("Diagnosis", SUBTITLE_FONT);
        document.add(diagnosisTitle);

        document.add(new Paragraph(" "));

        Paragraph diagnosisContent = new Paragraph(record.getDiagnosis(), NORMAL_FONT);
        document.add(diagnosisContent);

        // Add spacing
        document.add(new Paragraph(" "));
        document.add(new Paragraph(" "));

        // Add prescription section
        Paragraph prescriptionTitle = new Paragraph("Prescription", SUBTITLE_FONT);
        document.add(prescriptionTitle);

        document.add(new Paragraph(" "));

        Paragraph prescriptionContent = new Paragraph(record.getPrescription(), NORMAL_FONT);
        document.add(prescriptionContent);

        // Add notes if available
        if (record.getNotes() != null && !record.getNotes().isEmpty()) {
            // Add spacing
            document.add(new Paragraph(" "));
            document.add(new Paragraph(" "));

            // Add notes section
            Paragraph notesTitle = new Paragraph("Additional Notes", SUBTITLE_FONT);
            document.add(notesTitle);

            document.add(new Paragraph(" "));

            Paragraph notesContent = new Paragraph(record.getNotes(), NORMAL_FONT);
            document.add(notesContent);
        }

        // Add footer
        document.add(new Paragraph(" "));
        document.add(new Paragraph(" "));
        document.add(new Paragraph(" "));

        Paragraph footer = new Paragraph("This document was generated by Patient Tracker App on "
                + DateTimeUtils.formatDateTime(new Date()), SMALL_FONT);
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);
    }

    /**
     * Open a PDF file using the default PDF viewer
     */
    public static void openPdfFile(Context context, File pdfFile) {
        if (context == null || pdfFile == null || !pdfFile.exists()) {
            return;
        }

        try {
            // Get URI for the file using FileProvider
            Uri fileUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".provider",
                    pdfFile);

            // Create intent to view the PDF
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

            // Start the activity
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
            } else {
                Toast.makeText(context, "No PDF viewer app found", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening PDF: " + e.getMessage(), e);
            Toast.makeText(context, "Error opening PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Share a PDF file with other apps
     */
    public static void sharePdfFile(Context context, File pdfFile, String title) {
        if (context == null || pdfFile == null || !pdfFile.exists()) {
            return;
        }

        try {
            // Get URI for the file using FileProvider
            Uri fileUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".provider",
                    pdfFile);

            // Create intent to share the PDF
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Sharing medical record from Patient Tracker App");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Start the activity
            context.startActivity(Intent.createChooser(shareIntent, "Share Medical Record"));
        } catch (Exception e) {
            Log.e(TAG, "Error sharing PDF: " + e.getMessage(), e);
            Toast.makeText(context, "Error sharing PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}