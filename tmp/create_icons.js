const fs = require('fs');
const { execSync } = require('child_process');

// 1. Create the master SVG representing the exact uploaded ISKCON Shirpur logo
const logoSvg = `<?xml version="1.0" encoding="utf-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 680" width="600" height="680">
  <defs>
    <style>
      .red-stroke { stroke: #D8232A; stroke-width: 14; stroke-linecap: round; stroke-linejoin: round; fill: none; }
      .red-fill { fill: #D8232A; }
      .text-iskcon { font-family: 'DejaVu Serif', 'Times New Roman', serif; font-size: 82px; font-weight: bold; fill: #D8232A; text-anchor: middle; letter-spacing: 16px; }
      .text-shirpur { font-family: 'DejaVu Serif', 'Times New Roman', serif; font-size: 70px; font-weight: bold; fill: #D8232A; text-anchor: middle; letter-spacing: 12px; }
    </style>
  </defs>

  <!-- TOP LOTUS & TILAK EMBLEM -->
  <g transform="translate(0, 0)">
    <!-- Center Tilak Fork Lines -->
    <!-- Left Tilak line -->
    <path d="M 276,310 C 276,190 262,90 274,40 C 286,90 290,190 290,310" class="red-stroke" />
    <!-- Right Tilak line -->
    <path d="M 324,310 C 324,190 338,90 326,40 C 314,90 310,190 310,310" class="red-stroke" />

    <!-- Center Tulsi Leaf Droplet in solid red -->
    <path d="M 300,310 C 265,310 260,360 300,400 C 340,360 335,310 300,310 Z" class="red-fill" />

    <!-- Inner Tall Petals -->
    <!-- Left inner petal -->
    <path d="M 276,310 C 240,250 180,180 206,75 C 236,150 265,220 286,280" class="red-stroke" />
    <!-- Right inner petal -->
    <path d="M 324,310 C 360,250 420,180 394,75 C 364,150 335,220 314,280" class="red-stroke" />

    <!-- Upper-Middle Petals -->
    <!-- Left middle-high petal -->
    <path d="M 260,330 C 190,280 130,220 132,130 C 180,190 226,260 258,305" class="red-stroke" />
    <!-- Right middle-high petal -->
    <path d="M 340,330 C 410,280 470,220 468,130 C 420,190 374,260 342,305" class="red-stroke" />

    <!-- Lower-Middle Petals -->
    <!-- Left middle-low petal -->
    <path d="M 270,365 C 190,340 100,280 90,195 C 130,255 190,320 250,345" class="red-stroke" />
    <!-- Right middle-low petal -->
    <path d="M 330,365 C 410,340 500,280 510,195 C 470,255 410,320 350,345" class="red-stroke" />

    <!-- Bottom Outer Wide Petals -->
    <!-- Left outer petal -->
    <path d="M 285,385 C 180,395 70,350 40,280 C 85,340 185,380 270,380" class="red-stroke" />
    <!-- Right outer petal -->
    <path d="M 315,385 C 420,395 530,350 560,280 C 515,340 415,380 330,380" class="red-stroke" />

    <!-- Under-Petal Lobes / Bottom Curves -->
    <path d="M 300,370 C 240,370 170,350 140,290 C 190,370 260,390 300,395" class="red-stroke" />
    <path d="M 300,370 C 360,370 430,350 460,290 C 410,370 340,390 300,395" class="red-stroke" />
    
    <path d="M 300,390 C 230,405 180,370 170,320 C 200,395 260,410 300,410" class="red-stroke" />
    <path d="M 300,390 C 370,405 420,370 430,320 C 400,395 340,410 300,410" class="red-stroke" />
  </g>

  <!-- DIVIDER BAR 1 -->
  <line x1="90" y1="440" x2="510" y2="440" stroke="#D8232A" stroke-width="12" stroke-linecap="round" />

  <!-- ISKCON TEXT -->
  <text x="306" y="522" class="text-iskcon">ISKCON</text>

  <!-- DIVIDER BAR 2 -->
  <line x1="90" y1="550" x2="510" y2="550" stroke="#D8232A" stroke-width="12" stroke-linecap="round" />

  <!-- SHIRPUR TEXT -->
  <text x="305" y="632" class="text-shirpur">SHIRPUR</text>
</svg>
`;

fs.writeFileSync('/tmp/logo_master.svg', logoSvg);
console.log('Saved /tmp/logo_master.svg');
