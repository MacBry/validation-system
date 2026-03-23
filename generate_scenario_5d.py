import os
import random
from datetime import datetime, timedelta

def create_html(sn, filename, start_time, num_points=1440, interval_mins=5):
    # Minimal viable HTML imitating Testo Data
    html = f"""<html>
<head><meta charset="UTF-8"></head>
<body>
<table>
    <tr><th>Raport z pomiaru</th><td></td></tr>
    <tr><th>Data</th><td></td></tr>
    <tr><th>Raport wygenerowany przez</th><td></td></tr>
    <tr><th>...</th><td></td></tr>
    <tr><th>...</th><td></td></tr>
    <tr><td>{sn}</td><td></td></tr>
</table>
<br/>
<table>
    <tr>
        <th>Id</th>
        <th>Data</th>
        <th>Godzina</th>
        <th>Temp [°C]</th>
    </tr>
"""
    
    current_time = start_time
    for i in range(1, num_points + 1):
        # Ideal temp ~ 5.0 deg C (range 4.8 to 5.2)
        # Reference (sn ending in 10) can be ~ 20.0 deg C (range 19.5 - 20.5)
        if str(sn).endswith("10"):
            temp = 20.0 + random.uniform(-0.5, 0.5)
        else:
            temp = 5.0 + random.uniform(-0.2, 0.2)
            
        date_str = current_time.strftime("%d.%m.%Y")
        time_str = current_time.strftime("%H:%M:%S")
        temp_str = f"{temp:.1f}".replace('.', ',')
        
        html += f"""    <tr>
        <td>{i}</td>
        <td>{date_str}</td>
        <td>{time_str}</td>
        <td>{temp_str}</td>
    </tr>
"""
        current_time += timedelta(minutes=interval_mins)
        
    html += """</table>
</body>
</html>
"""
    
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(html)

def main():
    out_dir = "Scenariusz_5_DNI"
    os.makedirs(out_dir, exist_ok=True)
    
    # 5 days = 5 * 24 * 60 = 7200 minutes
    # 7200 minutes / 5 minutes = 1440 points
    num_points = 1440
    interval_mins = 5
    
    # Start measurements exactly 5 days ago
    start_time = datetime.now().replace(microsecond=0, second=0, minute=0) - timedelta(days=5)
    
    print(f"Generating 10 HTML files in {out_dir} ...")
    
    # 9 grid files
    for i in range(1, 10):
        sn = 50000000 + i
        filename = os.path.join(out_dir, f"{sn}.html")
        create_html(sn, filename, start_time=start_time, num_points=num_points, interval_mins=interval_mins)
        
    # 1 reference file
    ref_sn = 50000010
    ref_filename = os.path.join(out_dir, f"REF_{ref_sn}.html")
    create_html(ref_sn, ref_filename, start_time=start_time, num_points=num_points, interval_mins=interval_mins)
    
    print("Done! Files generated.")

if __name__ == "__main__":
    main()
