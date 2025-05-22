from fpdf import FPDF

def create_english_test_pdf():
    pdf = FPDF()
    pdf.add_page()
    pdf.set_font("Arial", size=12)
    
    # Add some text
    pdf.cell(200, 10, txt="English OCR Test Document", ln=True, align='C')
    pdf.ln(10)
    pdf.cell(200, 10, txt="This is a simple text file created for OCR testing purposes.", ln=True)
    pdf.cell(200, 10, txt="It contains plain English text that should be recognized by the OCR engine.", ln=True)
    pdf.cell(200, 10, txt="The text should be extracted correctly when processed through the application.", ln=True)
    
    # Save the PDF
    pdf.output("test_files/english_test_new.pdf")
    print("Created English test PDF")

def create_italian_test_pdf():
    pdf = FPDF()
    pdf.add_page()
    pdf.set_font("Arial", size=12)
    
    # Add some text
    pdf.cell(200, 10, txt="Documento di Prova OCR Italiano", ln=True, align='C')
    pdf.ln(10)
    pdf.cell(200, 10, txt="Questo è un semplice file di testo creato per testare la funzionalità OCR.", ln=True)
    pdf.cell(200, 10, txt="Contiene testo italiano semplice che dovrebbe essere riconosciuto dal motore OCR.", ln=True)
    pdf.cell(200, 10, txt="Il testo dovrebbe essere estratto correttamente quando elaborato attraverso l'applicazione.", ln=True)
    
    # Save the PDF
    pdf.output("test_files/italian_test_new.pdf")
    print("Created Italian test PDF")

if __name__ == "__main__":
    try:
        create_english_test_pdf()
        create_italian_test_pdf()
        print("Test PDFs created successfully")
    except Exception as e:
        print(f"Error creating PDFs: {e}")
