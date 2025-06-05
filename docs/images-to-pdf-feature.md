# Images to PDF Feature

## Overview
The "Generate PDF from images" feature allows users to upload multiple image files and combine them into a single PDF document.

## Features
- **Multiple Image Upload**: Support for uploading multiple images at once
- **Drag & Drop Interface**: Modern drag-and-drop functionality for easy file selection
- **Image Format Support**: Supports JPG, PNG, TIFF, BMP, and GIF formats
- **File Size Validation**: 10MB limit per image file
- **Live Preview**: Shows thumbnails of selected images before conversion
- **Customizable PDF Settings**:
  - Page Size: A4, A3, A5, Letter, Legal
  - Orientation: Portrait or Landscape
  - Image Scaling: Fit to page or original size
- **Progress Indication**: Loading modal during PDF generation
- **Direct Download**: Generated PDF automatically downloads

## Usage
1. Navigate to "Images to PDF" from the homepage or navigation menu
2. Upload images by:
   - Clicking the upload area and selecting files
   - Dragging and dropping images onto the upload area
3. Configure PDF settings (page size, orientation, scaling)
4. Click "Generate PDF" to create and download the PDF

## Technical Implementation
- **Backend**: Spring Boot controller using Apache PDFBox library
- **Frontend**: HTML5 with Bootstrap UI and JavaScript for file handling
- **File Processing**: Automatic image format detection and conversion
- **PDF Generation**: Each image is placed on a separate page with optional scaling

## File Structure
- `ImageToPdfController.java` - Main controller handling the image-to-PDF conversion
- `images-to-pdf.html` - Frontend template with upload interface
- Homepage integration with new feature card and quick tool button
- Navigation bar integration

## Error Handling
- File type validation (only image formats accepted)
- File size validation (10MB limit per file)
- Graceful error handling for corrupted or unsupported images
- User-friendly error messages
