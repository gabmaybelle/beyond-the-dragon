// Draws a structure template (.nbt) with Minecraft's own textures, isometric, on a slab of End
// stone, for looking at designs without starting the game.
//
//   node tools/render-structure.js <template.nbt> --out <png> [--size 700] [--jar <client or merged jar>]
//
// render-blocks.js is the gazebo mod's renderer, kept as it is; this only loads our templates.
const fs = require('fs'), path = require('path');
const nbt = require('./nbt.js');
const { render, writePng } = require('./render-blocks.js');
const args = process.argv.slice(2);
const opt = (n, d) => { const i = args.indexOf('--' + n); return i >= 0 ? args[i + 1] : d; };
const file = args.find(a => a.endsWith('.nbt'));
if (!file) { console.error('usage: node tools/render-structure.js <template.nbt> --out <png>'); process.exit(2); }

const t = nbt.plain(nbt.read(fs.readFileSync(file)));
const states = t.palette.map(p => ({ id: p.id || p.Name, props: p.properties || p.Properties || {} }));
const blocks = [];
for (const b of t.blocks) {
	const [x, y, z] = b.pos, s = states[b.state];
	if (s.id === 'minecraft:air' || s.id === 'minecraft:structure_void') continue;
	blocks.push({ x, y, z, id: s.id, props: s.props, nbtData: b.nbt });
}
// An island under it: end stone to three blocks below the template, a little wider than it.
for (let x = -1; x <= t.size[0]; x++) for (let z = -1; z <= t.size[2]; z++) for (let y = -3; y <= -1; y++) {
	blocks.push({ x, y, z, id: 'minecraft:end_stone', props: {} });
}
const size = +opt('size', 700);
writePng(opt('out', file.replace(/\.nbt$/, '.png')), size, size, render(blocks, size, Math.round(size * 0.04)));
