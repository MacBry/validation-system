import fitz
import io
import sys
try:
    from PIL import Image
    import pytesseract
    pytesseract.pytesseract.tesseract_cmd = r'C:\Program Files\Tesseract-OCR\tesseract.exe'
except ImportError:
    pass

doc = fitz.open(r"C:\Users\macie\Desktop\Day zero\Przykładowy pdf TESTO 2.pdf")
page = doc[0]
image_list = page.get_images()

with open("pdf_image_text.txt", "w", encoding="utf-8") as f:
    if image_list:
        xref = image_list[0][0]
        base_image = doc.extract_image(xref)
        image_bytes = base_image["image"]
        f.write("Extracted image... size: " + str(len(image_bytes)) + "\n")
        
        try:
            image = Image.open(io.BytesIO(image_bytes))
            text = pytesseract.image_to_string(image)
            f.write("--- OCR TEXT ---\n" + text)
            print("OCR Success")
        except Exception as e:
            f.write("OCR Error: " + str(e))
            print("OCR Error", e)
    else:
        f.write("No images found.\n")
