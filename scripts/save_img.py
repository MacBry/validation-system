import fitz
doc = fitz.open(r"C:\Users\macie\Desktop\Day zero\Przykładowy pdf TESTO 2.pdf")
page = doc[0]
image_list = page.get_images()
if image_list:
    xref = image_list[0][0]
    base_image = doc.extract_image(xref)
    image_bytes = base_image["image"]
    with open("extracted_header.jpg", "wb") as f:
        f.write(image_bytes)
    print("Saved image")
