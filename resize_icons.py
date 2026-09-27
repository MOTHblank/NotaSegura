import os
import math
from PIL import Image, ImageDraw

def process_icons():
    # Paths
    master_path = r"C:\Users\Matheus\.gemini\antigravity\brain\89f62dd7-b876-40ad-b39d-81ca9b6acdcd\notasegura_logo_clean_1782168462491.png"
    res_dir = r"c:\Users\Matheus\AndroidStudioProjects\NotaSegura\app\src\main\res"
    
    # 1. Load master image
    print(f"Loading master image from {master_path}...")
    img = Image.open(master_path).convert("RGBA")
    width, height = img.size
    
    # 2. Extract foreground symbol using chroma-keying
    # Corner colors are approx (8, 67, 145). Let's use that as background reference.
    bg_r, bg_g, bg_b = 8, 67, 145
    
    print("Isolating foreground emblem using anti-aliased distance keying...")
    # Create transparent image for foreground
    fg_img = Image.new("RGBA", (width, height))
    pixels_in = img.load()
    pixels_out = fg_img.load()
    
    # Keying parameters
    threshold_min = 15.0
    threshold_max = 35.0
    
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels_in[x, y]
            # Calculate Euclidean distance in RGB color space
            dist = math.sqrt((r - bg_r)**2 + (g - bg_g)**2 + (b - bg_b)**2)
            
            if dist < threshold_min:
                alpha = 0
            elif dist > threshold_max:
                alpha = 255
            else:
                # Linear interpolation for anti-aliasing
                alpha = int(((dist - threshold_min) / (threshold_max - threshold_min)) * 255)
            
            # Apply alpha
            pixels_out[x, y] = (r, g, b, alpha)
            
    # 3. Crop to actual bounding box of the foreground emblem
    bbox = fg_img.getbbox()
    if bbox:
        print(f"Emblem bounding box: {bbox}")
        emblem = fg_img.crop(bbox)
    else:
        print("Warning: Bounding box not found, using full image.")
        emblem = fg_img
        
    # Target Background Color: Secure Blue (#0D47A1)
    secure_blue = (13, 71, 161, 255)
    
    # 4. Generate Adaptive Foreground (432x432 px at xxxhdpi)
    print("Generating adaptive foreground icon...")
    adaptive_fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    # Standard safe zone is 66dp (264px at xxxhdpi), let's fit the emblem inside a 240px box to look clean
    emblem_w, emblem_h = emblem.size
    scale = min(240 / emblem_w, 240 / emblem_h)
    new_w = int(emblem_w * scale)
    new_h = int(emblem_h * scale)
    resized_emblem_fg = emblem.resize((new_w, new_h), Image.Resampling.LANCZOS)
    
    # Paste centered
    offset_x = (432 - new_w) // 2
    offset_y = (432 - new_h) // 2
    adaptive_fg.paste(resized_emblem_fg, (offset_x, offset_y), resized_emblem_fg)
    
    # Save adaptive foreground to drawable
    fg_output_path = os.path.join(res_dir, "drawable", "ic_launcher_foreground.webp")
    adaptive_fg.save(fg_output_path, "WEBP", quality=100)
    print(f"Saved adaptive foreground to {fg_output_path}")
    
    # Densities for legacy icons
    # Format: (folder_name, size_px)
    densities = [
        ("mipmap-mdpi", 48),
        ("mipmap-hdpi", 72),
        ("mipmap-xhdpi", 96),
        ("mipmap-xxhdpi", 144),
        ("mipmap-xxxhdpi", 192),
    ]
    
    for folder, size in densities:
        # Create output directories if they don't exist
        folder_path = os.path.join(res_dir, folder)
        os.makedirs(folder_path, exist_ok=True)
        
        # --- A. Legacy Square Icon (with rounded corners) ---
        # Draw base rounded rectangle canvas
        square_icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        draw_sq = ImageDraw.Draw(square_icon)
        
        # Corner radius is typically 15% of size
        radius = max(4, int(size * 0.15))
        draw_sq.rounded_rectangle([0, 0, size, size], radius=radius, fill=secure_blue)
        
        # Emblem size inside legacy square is about 65% of full icon size
        target_emb_size = int(size * 0.65)
        scale_sq = min(target_emb_size / emblem_w, target_emb_size / emblem_h)
        emb_w_sq = int(emblem_w * scale_sq)
        emb_h_sq = int(emblem_h * scale_sq)
        resized_sq = emblem.resize((emb_w_sq, emb_h_sq), Image.Resampling.LANCZOS)
        
        offset_x_sq = (size - emb_w_sq) // 2
        offset_y_sq = (size - emb_h_sq) // 2
        square_icon.paste(resized_sq, (offset_x_sq, offset_y_sq), resized_sq)
        
        square_output_path = os.path.join(folder_path, "ic_launcher.webp")
        square_icon.save(square_output_path, "WEBP", quality=90)
        
        # --- B. Legacy Round Icon ---
        # Draw base circular canvas
        round_icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        draw_rd = ImageDraw.Draw(round_icon)
        draw_rd.ellipse([0, 0, size, size], fill=secure_blue)
        
        # Emblem size inside legacy round is about 60% of full icon size (extra padding for round mask)
        target_emb_rd_size = int(size * 0.60)
        scale_rd = min(target_emb_rd_size / emblem_w, target_emb_rd_size / emblem_h)
        emb_w_rd = int(emblem_w * scale_rd)
        emb_h_rd = int(emblem_h * scale_rd)
        resized_rd = emblem.resize((emb_w_rd, emb_h_rd), Image.Resampling.LANCZOS)
        
        offset_x_rd = (size - emb_w_rd) // 2
        offset_y_rd = (size - emb_h_rd) // 2
        round_icon.paste(resized_rd, (offset_x_rd, offset_y_rd), resized_rd)
        
        round_output_path = os.path.join(folder_path, "ic_launcher_round.webp")
        round_icon.save(round_output_path, "WEBP", quality=90)
        
        print(f"Generated {folder} launcher icons (size: {size}x{size}px).")

if __name__ == "__main__":
    process_icons()
    print("All icons successfully generated!")
