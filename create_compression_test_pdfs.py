from fpdf import FPDF
from PIL import Image
import os

def create_compression_test_pdf():
    """Create a PDF with large images for compression testing"""
    pdf = FPDF()
    pdf.add_page()
    pdf.set_font("Arial", size=12)
    
    # Add title
    pdf.cell(200, 10, txt="PDF Compression Test Document", ln=True, align='C')
    pdf.ln(10)
    
    # Add some text
    pdf.cell(200, 10, txt="This PDF contains large images to test compression functionality.", ln=True)
    pdf.ln(5)
    pdf.cell(200, 10, txt="Original images should be compressed to reduce file size.", ln=True)
    pdf.ln(10)
    
    # Check if we have image files in imgs folder
    imgs_dir = "./imgs"
    if os.path.exists(imgs_dir):
        img_files = [f for f in os.listdir(imgs_dir) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
        
        for i, img_file in enumerate(img_files[:2]):  # Use up to 2 images
            img_path = os.path.join(imgs_dir, img_file)
            try:
                # Add the image to PDF
                pdf.image(img_path, x=10, y=pdf.get_y() + 10, w=180)
                pdf.ln(60)  # Move down after image
                pdf.cell(200, 10, txt=f"Image {i+1}: {img_file}", ln=True)
                pdf.ln(10)
            except Exception as e:
                print(f"Error adding image {img_file}: {e}")
    else:
        # Create a simple colored rectangle if no images available
        pdf.cell(200, 10, txt="No images found - creating text-based test", ln=True)
        
        # Add a lot of text to make the file larger
        for i in range(20):
            pdf.cell(200, 5, txt=f"Line {i+1}: This is repeated text to make the PDF larger for testing compression.", ln=True)
    
    # Save the PDF
    if not os.path.exists("test_files"):
        os.makedirs("test_files")
    
    output_path = "test_files/compression_test.pdf"
    pdf.output(output_path)
    
    # Get file size
    file_size = os.path.getsize(output_path)
    print(f"Created compression test PDF: {output_path}")
    print(f"File size: {file_size:,} bytes ({file_size/1024:.1f} KB)")
    
    return output_path

def create_large_image_pdf():
    """Create a PDF with artificially large images"""
    try:
        from PIL import Image, ImageDraw
        
        # Create a large image
        width, height = 2000, 1500  # Large dimensions
        img = Image.new('RGB', (width, height), color='white')
        draw = ImageDraw.Draw(img)
        
        # Draw some patterns to make it realistic
        for i in range(0, width, 50):
            draw.line([(i, 0), (i, height)], fill='lightblue', width=1)
        for i in range(0, height, 50):
            draw.line([(0, i), (width, i)], fill='lightblue', width=1)
        
        # Add some colored rectangles
        draw.rectangle([100, 100, 500, 400], fill='red', outline='black')
        draw.rectangle([600, 200, 1000, 500], fill='green', outline='black')
        draw.rectangle([1100, 300, 1500, 600], fill='blue', outline='black')
        
        # Save the image temporarily
        temp_img_path = "temp_large_image.png"
        img.save(temp_img_path, 'PNG')
        
        # Create PDF with this large image
        pdf = FPDF()
        pdf.add_page()
        pdf.set_font("Arial", size=16)
        
        pdf.cell(200, 10, txt="Large Image Compression Test", ln=True, align='C')
        pdf.ln(10)
        pdf.cell(200, 8, txt=f"Original image size: {width}x{height} pixels", ln=True)
        pdf.ln(10)
        
        # Add the large image
        pdf.image(temp_img_path, x=10, y=pdf.get_y(), w=190)
        
        # Save PDF
        output_path = "test_files/large_image_test.pdf"
        pdf.output(output_path)
        
        # Clean up temp image
        os.remove(temp_img_path)
        
        file_size = os.path.getsize(output_path)
        print(f"Created large image test PDF: {output_path}")
        print(f"File size: {file_size:,} bytes ({file_size/1024:.1f} KB)")
        
        return output_path
        
    except ImportError:
        print("PIL not available - skipping large image test PDF creation")
        return None

if __name__ == "__main__":
    try:
        print("Creating test PDFs for compression testing...")
        
        # Create basic compression test PDF
        pdf1 = create_compression_test_pdf()
        
        # Create large image test PDF
        pdf2 = create_large_image_pdf()
        
        print("\nTest PDFs created successfully!")
        print("\nTo test compression:")
        print("1. Start the application: mvnw.cmd spring-boot:run")
        print("2. Go to http://localhost:8080/compress")
        print("3. Upload the test PDF files")
        print("4. Enable 'Compress Images' option")
        print("5. Compare original and compressed file sizes")
        
    except Exception as e:
        print(f"Error creating test PDFs: {e}")
        import traceback
        traceback.print_exc()
