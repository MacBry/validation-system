try:
    import fitz
    doc = fitz.open(r"C:\Users\macie\Desktop\Day zero\Przykładowy pdf TESTO.pdf")
    for page in doc.pages(0, min(10, doc.page_count)):
        print(page.get_text())
except Exception as e:
    import PyPDF2
    with open(r"C:\Users\macie\Desktop\Day zero\Przykładowy pdf TESTO.pdf", 'rb') as f:
        reader = PyPDF2.PdfReader(f)
        for i in range(min(10, len(reader.pages))):
            print(reader.pages[i].extract_text())
