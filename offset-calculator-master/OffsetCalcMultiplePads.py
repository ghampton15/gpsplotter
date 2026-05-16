import csv
import math
import os
from collections import defaultdict

# === SETTINGS ===
OFFSET_DISTANCE = 5.0  # feet
input_path = r"C:\Users\Gant\Downloads\test.csv"
output_path = os.path.splitext(input_path)[0] + "_offsets.csv"


# === FUNCTIONS ===
def read_points(csv_path):
    pads = defaultdict(list)
    with open(csv_path, newline='') as csvfile:
        reader = csv.DictReader(csvfile)
        for row in reader:
            code = row.get('Code', '').strip().upper()
            if code.startswith('BLDG_'):
                pads[code].append({
                    'PointName': row['Name'],
                    'Easting': float(row['Easting']),
                    'Northing': float(row['Northing']),
                    'Elevation': float(row['Elevation']),
                })
    return pads


def calculate_diagonal_direction(corner1, corner2):
    dx = corner2['Easting'] - corner1['Easting']
    dy = corner2['Northing'] - corner1['Northing']
    angle = math.degrees(math.atan2(dy, dx))
    return angle


def offset_outward(corner, opposite_corner, distance):
    # Calculate the direction of the diagonal
    diagonal_angle = calculate_diagonal_direction(corner, opposite_corner)

    # Flip direction 180° to move OUTWARD
    angle_radians = math.radians(diagonal_angle + 180)

    dx = math.cos(angle_radians)
    dy = math.sin(angle_radians)

    offset_x = corner['Easting'] + dx * distance
    offset_y = corner['Northing'] + dy * distance

    return {
        'PointName': corner['PointName'] + "_OFFSET",
        'Easting': round(offset_x, 3),
        'Northing': round(offset_y, 3),
        'Elevation': round(corner['Elevation'], 3)
    }


def generate_offsets(pads_dict):
    all_offsets = []
    for pad_points in pads_dict.values():
        n = len(pad_points)
        for i, pt in enumerate(pad_points):
            # Get the diagonal opposite point (2 positions ahead in the list)
            opposite_pt = pad_points[(i + 2) % n]
            offset = offset_outward(pt, opposite_pt, OFFSET_DISTANCE)
            all_offsets.append(offset)
    return all_offsets


def write_points(filename, points):
    with open(filename, 'w', newline='') as csvfile:
        fieldnames = ['PointName', 'Easting', 'Northing', 'Elevation']
        writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(points)


# === RUN SCRIPT ===
building_pads = read_points(input_path)
offset_points = generate_offsets(building_pads)
write_points(output_path, offset_points)

print(f"✅ Offset points saved to:\n{output_path}")
