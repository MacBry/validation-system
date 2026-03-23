import fitz
doc = fitz.open(r"C:\Users\macie\Desktop\Day zero\Przykładowy pdf TESTO 2.pdf")
with open("pdf_annots.txt", "w", encoding="utf-8") as f:
    for page in doc:
        f.write(f"--- PAGE {page.number} ---\n")
        # Annotations (widgets/forms)
        for annot in page.annots():
            f.write(f"Annot: {annot.info}\n")
        # Images
        image_list = page.get_images()
        for img in image_list:
            f.write(f"Image: {img}\n")
        # Raw Dict
        f.write("Links: " + str(page.get_links()) + "\n")
