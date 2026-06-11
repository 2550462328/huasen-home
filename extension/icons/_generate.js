// Generate solid-fill PNG placeholders (#1e80ff) at 16/48/128 px.
// Hand-built minimal PNG (no deps): IHDR + IDAT (zlib-deflate of filtered scanlines) + IEND.
// Run once: `node extension/icons/_generate.js` → writes icon16.png, icon48.png, icon128.png.
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const COLOR = { r: 0x1e, g: 0x80, b: 0xff };

const crcTable = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();
function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = crcTable[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const typeBuf = Buffer.from(type, 'ascii');
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([len, typeBuf, data, crc]);
}
function makePng(size) {
  const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8;        // bit depth
  ihdr[9] = 2;        // color type RGB
  ihdr[10] = 0;       // compression
  ihdr[11] = 0;       // filter
  ihdr[12] = 0;       // interlace
  // raw scanlines: filter-byte 0 + RGB triplets
  const stride = size * 3 + 1;
  const raw = Buffer.alloc(stride * size);
  for (let y = 0; y < size; y++) {
    raw[y * stride] = 0;
    for (let x = 0; x < size; x++) {
      const o = y * stride + 1 + x * 3;
      raw[o] = COLOR.r;
      raw[o + 1] = COLOR.g;
      raw[o + 2] = COLOR.b;
    }
  }
  const idat = zlib.deflateSync(raw);
  return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', idat), chunk('IEND', Buffer.alloc(0))]);
}

[16, 48, 128].forEach((s) => {
  const out = path.join(__dirname, `icon${s}.png`);
  fs.writeFileSync(out, makePng(s));
  console.log('wrote', out, fs.statSync(out).size, 'bytes');
});
