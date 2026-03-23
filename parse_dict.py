import fitz

doc = fitz.open(r"C:\Users\macie\Desktop\Day zero\Przykładowy pdf TESTO 2.pdf")
with open("pdf_dict.txt", "w", encoding="utf-8") as f:
    f.write("=== TOC ===\n")
    for item in doc.get_toc():
        f.write(str(item) + "\n")
    f.write("\n=== META ===\n" + str(doc.metadata) + "\n")
