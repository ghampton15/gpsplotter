import csv
import math
import os

# === SETTINGS ===
OFFSET_DISTANCE = 5.0  # feet
input_path = r"C:\Users\Gant\Downloads\test.csv"
output_path = os.path.splitext(input_path)[0] + "_offsets.csv"

# === FUNCTIONS ===
def read_points(csv_path):
    with open(csv_path, newline='') as csvfile:
        reader = csv.DictReader(csvfile)
        return [{
            'PointName': row['Name'],
            'Easting': float(row['Easting']),
            'Northing': float(row['Northing']),
            'Elevation': float(row['Elevation'])
        } for row in reader]

def unit_vector(dx, dy):
    length = math.hypot(dx, dy)
    return (dx / length, dy / length) if length != 0 else (0, 0)

def offset_diagonal(corner, prev, next, distance):
    vec1 = (prev['Easting'] - corner['Easting'], prev['Northing'] - corner['Northing'])
    vec2 = (next['Easting'] - corner['Easting'], next['Northing'] - corner['Northing'])

    u1 = unit_vector(*vec1)
    u2 = unit_vector(*vec2)

    avg_dx = u1[0] + u2[0]
    avg_dy = u1[1] + u2[1]

    unit_avg = unit_vector(avg_dx, avg_dy)

    offset_x = corner['Easting'] + unit_avg[0] * distance
    offset_y = corner['Northing'] + unit_avg[1] * distance

    return {
        'PointName': corner['PointName'] + "_OFFSET",
        'Easting': round(offset_x, 3),
        'Northing': round(offset_y, 3),
        'Elevation': round(corner['Elevation'], 3)
    }

def generate_offsets(points):
    offsets = []
    n = len(points)
    for i, pt in enumerate(points):
        prev_pt = points[(i - 1) % n]
        next_pt = points[(i + 1) % n]
        offset = offset_diagonal(pt, prev_pt, next_pt, OFFSET_DISTANCE)
        offsets.append(offset)
    return offsets

def write_points(filename, points):
    with open(filename, 'w', newline='') as csvfile:
        fieldnames = ['PointName', 'Easting', 'Northing', 'Elevation']
        writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(points)

# === RUN SCRIPT ===
original_points = read_points(input_path)
offset_points = generate_offsets(original_points)
write_points(output_path, offset_points)

print(f"Offset points saved to:\n{output_path}")
