try:
    import fitz
    doc = fitz.open(r"C:\Users\macie\Desktop\Day zero\Przykładowy pdf TESTO.pdf")
    with open("full_pdf_blocks.txt", "w", encoding="utf-8") as f:
        for page in doc:
            f.write(page.get_text("blocks") + "\n\n")
except Exception as e:
    import PyPDF2
    with open("full_pdf_blocks.txt", "w", encoding="utf-8") as f:
        reader = PyPDF2.PdfReader(r"C:\Users\macie\Desktop\Day zero\Przykładowy pdf TESTO.pdf")
        for page in reader.pages:
            f.write(page.extract_text() + "\n\n")
