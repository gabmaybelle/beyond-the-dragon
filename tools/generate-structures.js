// Builds the Outer Reaches structure templates (.nbt) from vanilla's own End City and fossil
// pieces, read straight from the game jar, plus the written book that lists what the restorable
// ruin is missing. Run from the repo root:
//   node tools/generate-structures.js [--jar <client or Loom merged jar>]
// The observatory comes as two templates: the intact one, which the restoration code compares
// against, and the ruin, which is what generates. The wreck, the graveyard and the sinking city
// are only ever ruins. Edit this file, never the .nbt files.
const fs = require('fs'), path = require('path'), os = require('os');
const { Structure } = require('./structure.js');
const nbt = require('./nbt.js');
const { list, T, byte } = nbt;
const { openZip } = require('./render-blocks.js');

const NS = 'beyond_the_dragon';
const ROOT = path.join(__dirname, '..', 'common', 'src', 'main', 'resources', 'data', NS);
const args = process.argv.slice(2);
const opt = (n, d) => { const i = args.indexOf('--' + n); return i >= 0 ? args[i + 1] : d; };
const JAR = opt('jar', path.join(os.homedir(), '.gradle', 'caches', 'fabric-loom', 'minecraftMaven', 'net', 'minecraft',
	'minecraft-merged-deobf', '26.3', 'minecraft-merged-deobf-26.3.jar'));
const jar = openZip(JAR);
const out = (rel, buf) => {
	const f = path.join(ROOT, ...rel.split('/'));
	fs.mkdirSync(path.dirname(f), { recursive: true });
	fs.writeFileSync(f, buf);
};

/** A builder that records every block so a ruin can be derived and the book written. */
class Build {
	constructor(size) {
		this.size = size;
		this.blocks = new Map();
		this.shulkers = [];
		this.phantoms = [];
	}
	/** A phantom in the air; it anchors itself where it wakes, so it circles the ruin. */
	phantom(x, y, z, deathLoot) {
		this.phantoms.push([x, y, z, deathLoot]);
	}
	/** A shulker sitting on the block at x, y - 1, z (the design caps these: a few per ruin). */
	shulker(x, y, z) {
		const solid = (xx, yy, zz) => { const e = this.get(xx, yy, zz); return e && e.state !== 'minecraft:air'; };
		let yy = y;
		while (yy < this.size[1] - 1 && (solid(x, yy, z) || !solid(x, yy - 1, z))) yy++;
		if (yy >= this.size[1] - 1) throw new Error(`no floor for a shulker near ${x},${y},${z}`);
		this.shulkers.push([x, yy, z]);
	}
	set(x, y, z, state, nbtData) {
		if (x < 0 || y < 0 || z < 0 || x >= this.size[0] || y >= this.size[1] || z >= this.size[2]) throw new Error(`out of the template: ${x},${y},${z}`);
		this.blocks.set(`${x},${y},${z}`, { state, nbt: nbtData });
	}
	get(x, y, z) {
		return this.blocks.get(`${x},${y},${z}`);
	}
	del(x, y, z) {
		this.blocks.delete(`${x},${y},${z}`);
	}
	/** Every block, as [x, y, z, entry]. */
	all() {
		return [...this.blocks].map(([k, v]) => [...k.split(',').map(Number), v]);
	}
	/** A copy with the listed positions turned to air (a ruin leaves nothing where the blocks were). */
	ruin(missing) {
		const r = new Build(this.size);
		for (const [k, v] of this.blocks) r.blocks.set(k, v);
		for (const [x, y, z] of missing) r.blocks.set(`${x},${y},${z}`, { state: 'minecraft:air' });
		r.shulkers = [...this.shulkers];
		r.phantoms = [...this.phantoms];
		return r;
	}
	toNbt() {
		const s = new Structure(this.size);
		for (const [k, { state, nbt: n }] of this.blocks) {
			const [x, y, z] = k.split(',').map(Number);
			s.set(x, y, z, state, n);
		}
		for (const [x, y, z] of this.shulkers) s.addEntity(x + 0.5, y, z + 0.5, { id: 'minecraft:shulker', PersistenceRequired: byte(1) });
		for (const [x, y, z, deathLoot] of this.phantoms) {
			const n = { id: 'minecraft:phantom', size: 1, PersistenceRequired: byte(1) };
			if (deathLoot) n.DeathLootTable = deathLoot;
			s.addEntity(x + 0.5, y, z + 0.5, n);
		}
		return s.toNbt();
	}
}

/** A vanilla template from the jar: its size and blocks as [x, y, z, state, nbt]. */
function vanilla(name) {
	const t = nbt.plain(nbt.read(jar.read(`data/minecraft/structure/${name}.nbt`)));
	const states = t.palette.map(p => {
		const props = p.Properties || p.properties;
		return props ? `${p.Name || p.id}[${Object.entries(props).map(([k, v]) => `${k}=${v}`).join(',')}]` : (p.Name || p.id);
	});
	return { size: t.size, blocks: t.blocks.map(b => [...b.pos, states[b.state], b.nbt]) };
}

/**
 * Stamps a vanilla template into a build at an offset. Vanilla's marker structure blocks (they
 * stand for chests, sentries and the elytra) become air; chests get our loot table.
 */
function stamp(b, name, ox, oy, oz, loot) {
	for (const [x, y, z, state, n] of vanilla(name).blocks) {
		if (state.startsWith('minecraft:structure_block') || state.startsWith('minecraft:jigsaw')) {
			b.set(x + ox, y + oy, z + oz, 'minecraft:air');
		} else if (state.startsWith('minecraft:chest') && loot) {
			b.set(x + ox, y + oy, z + oz, state, { LootTable: loot });
		} else {
			// Vanilla block-entity data (banners, ender chests...) is read untyped and can't be
			// written back as it is; the blocks keep their defaults, which is fine for ruins.
			b.set(x + ox, y + oy, z + oz, state);
		}
	}
}

/** A small deterministic random, so the same design comes out every run. */
function rng(seed) {
	let s = seed >>> 0;
	return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; };
}

const plain = id => id.replace(/^minecraft:/, '').replace(/\[.*$/, '').replace(/_/g, ' ');

/** The restoration book: per layer, what the ruin is missing, counted by block. */
function book(title, intro, missing, intact) {
	const byLayer = new Map();
	for (const [x, y, z] of missing) {
		const e = intact.get(x, y, z);
		if (!e || e.state === 'minecraft:air') continue;
		if (!byLayer.has(y)) byLayer.set(y, new Map());
		const layer = byLayer.get(y);
		layer.set(plain(e.state), (layer.get(plain(e.state)) || 0) + 1);
	}
	const pages = [intro];
	let page = '';
	for (const y of [...byLayer.keys()].sort((a, b) => a - b)) {
		let text = `Layer ${y}:\n`;
		for (const [name, n] of byLayer.get(y)) text += `${n} ${name}\n`;
		if ((page + text).length > 220) { pages.push(page); page = ''; }
		page += text + '\n';
	}
	if (page) pages.push(page);
	return {
		id: 'minecraft:written_book', count: 1,
		components: {
			'minecraft:written_book_content': {
				title: { raw: title }, author: 'the last keeper',
				pages: list(T.COMPOUND, pages.map(p => ({ raw: p }))),
			},
		},
	};
}

// --- the observatory ---------------------------------------------------------------------------
// Vanilla's fat tower (base, one middle, top) with a telescope of end rods on the roof, a lectern
// inside the top floor and the chests holding our loot. The ruin has lost a quarter of the roof,
// a bite of the middle floor's wall, and the telescope's eyepiece.
function observatory() {
	const b = new Build([21, 24, 21]);
	stamp(b, 'end_city/fat_tower_base', 4, 0, 4);
	stamp(b, 'end_city/fat_tower_middle', 4, 4, 4);
	stamp(b, 'end_city/fat_tower_top', 2, 12, 2, `${NS}:chests/observatory`);
	// The telescope: a purpur pillar on the roof's middle with end rods up and then north.
	const cx = 10, cz = 10;
	let roof = 23;
	while (roof > 0 && !b.get(cx, roof, cz)) roof--;
	b.set(cx, roof + 1, cz, 'minecraft:purpur_pillar[axis=y]');
	b.set(cx, roof + 2, cz, 'minecraft:end_rod[facing=up]');
	b.set(cx, roof + 3, cz, 'minecraft:end_rod[facing=up]');
	b.set(cx, roof + 4, cz, 'minecraft:end_rod[facing=north]');
	b.set(cx, roof + 4, cz - 1, 'minecraft:end_rod[facing=north]');
	b.set(cx, roof + 4, cz - 2, 'minecraft:end_rod[facing=north]');
	// The lectern on the top floor (the floor the chests stand on), facing the room.
	let floor = 13;
	while (floor < 23 && b.get(cx, floor, cz) && b.get(cx, floor, cz).state !== 'minecraft:air') floor++;
	b.set(cx - 2, floor, cz + 3, 'minecraft:lectern[facing=north,has_book=true,powered=false]', { Book: { id: 'minecraft:written_book', count: 1 }, Page: 0 });
	b.shulker(cx + 3, floor, cz - 3);
	b.shulker(cx - 3, 5, cz);
	return { build: b, roof, floor };
}

function observatoryMissing(o) {
	const { build: b, roof } = o;
	const missing = [];
	// The north-east quarter of the roof.
	for (const [x, y, z, e] of b.all()) {
		if (y >= roof - 2 && y <= roof && x >= 11 && z <= 9 && e.state !== 'minecraft:air') missing.push([x, y, z]);
	}
	// A bite from the middle floor's east wall.
	for (let y = 6 + PLINTH; y <= 10 + PLINTH; y++) for (let z = 7; z <= 12; z++) for (let x = 14; x <= 16; x++) {
		const e = b.get(x, y, z);
		if (e && e.state !== 'minecraft:air' && !e.state.includes('pillar')) missing.push([x, y, z]);
	}
	// The eyepiece.
	for (const z of [10, 9, 8]) missing.push([10, roof + 4, z]);
	return missing;
}

// --- the wrecked End ship -----------------------------------------------------------------------
// Vanilla's End ship, run aground: the hull torn open on the east side amidships, the mast
// snapped with its top lying along the deck, the sails gone, obsidian and end stone round the
// wound. The figurehead stays. The two chests hold our loot.
function wreck() {
	const ship = vanilla('end_city/ship');
	const b = new Build([ship.size[0] + 6, ship.size[1], ship.size[2] + 2]);
	stamp(b, 'end_city/ship', 3, 0, 1, `${NS}:chests/wreck`);
	const rand = rng(7);
	// Find the mast: the tallest column of purpur pillars.
	let mastX = -1, mastZ = -1, mastTop = -1;
	for (const [x, y, z, e] of b.all()) {
		if (e.state.startsWith('minecraft:purpur_pillar') && y > mastTop) { mastTop = y; mastX = x; mastZ = z; }
	}
	// The deck: the highest block beside the mast's foot (the mast runs down through the hull).
	let deck = 0;
	for (let y = 0; y < mastTop; y++) if (b.get(mastX + 1, y, mastZ) && b.get(mastX + 1, y, mastZ).state !== 'minecraft:air') deck = y;
	// The sails and the crow's nest (everything high up) go, and the mast is snapped four blocks
	// above the deck; its upper length lies along the deck toward the bow (south, +z).
	const sailLine = deck + 9;
	let laid = 0;
	for (const [x, y, z, e] of b.all()) {
		if (e.state === 'minecraft:air') continue;
		const mast = x === mastX && z === mastZ && e.state.includes('pillar');
		if (y > sailLine || (mast && y > deck + 4)) {
			b.del(x, y, z);
			if (mast) laid++;
		}
	}
	for (let i = 0; i < laid; i++) {
		const z = mastZ + 3 + i;
		if (z < b.size[2] && !b.get(mastX + 1, deck + 1, z)) b.set(mastX + 1, deck + 1, z, 'minecraft:purpur_pillar[axis=z]');
	}
	// The wound: the east side amidships, most of the hull and deck gone.
	for (let z = mastZ - 6; z <= mastZ - 1; z++) for (let x = mastX + 1; x < b.size[0]; x++) for (let y = 0; y <= deck + 1; y++) {
		const e = b.get(x, y, z);
		if (e && e.state !== 'minecraft:air' && rand() < 0.8) b.del(x, y, z);
	}
	b.shulker(mastX - 1, deck + 1, mastZ - 2);
	b.phantom(mastX, deck + 10, mastZ);
	b.phantom(mastX - 4, deck + 12, mastZ + 6);
	b.phantom(mastX + 4, deck + 11, mastZ - 6);
	// Debris where it struck: obsidian and end stone, low on the east side.
	for (let i = 0; i < 16; i++) {
		const x = mastX + 2 + Math.floor(rand() * 5), z = mastZ - 7 + Math.floor(rand() * 9), y = Math.floor(rand() * 2);
		if (!b.get(x, y, z)) b.set(x, y, z, rand() < 0.5 ? 'minecraft:obsidian' : 'minecraft:end_stone');
	}
	return b;
}

// --- the dragon graveyard -----------------------------------------------------------------------
// Vanilla's largest fossil spine and skull laid out as a dragon's remains, with ribs of bone
// blocks arching over the spine, the dragon head set on the skull, and the chest, with the
// upgrade template, under the ribs where the heart was.
function graveyard() {
	const b = new Build([29, 17, 50]);
	const cx = 14;
	const rand = rng(11);
	// The skull: a mound of bone blocks, eye sockets of air, the dragon head set on its brow.
	for (let x = cx - 3; x <= cx + 3; x++) for (let z = 1; z <= 8; z++) for (let y = 1; y <= 5; y++) {
		const d = Math.hypot((x - cx) / 3.5, (z - 4.5) / 4, (y - 1) / 4.5);
		if (d <= 1) b.set(x, y, z, 'minecraft:bone_block[axis=y]');
	}
	for (const [x, z] of [[cx - 2, 3], [cx + 2, 3]]) for (const y of [3, 4]) b.del(x, y, z);
	b.set(cx, 6, 4, 'minecraft:dragon_head[rotation=8,powered=false]');
	// The spine: three vanilla fossil spines end to end.
	stamp(b, 'fossil/spine_4', cx - 4, 1, 9);
	stamp(b, 'fossil/spine_3', cx - 3, 1, 22);
	stamp(b, 'fossil/spine_2', cx - 3, 1, 35);
	// Ribs: arches two blocks thick over the spine, tallest in the middle, two broken.
	for (let i = 0; i < 8; i++) {
		const z = 10 + i * 4;
		const span = 11 - Math.abs(i - 3.5) * 1.4;
		const height = 13 - Math.abs(i - 3.5) * 1.6;
		for (let side = -1; side <= 1; side += 2) {
			for (let step = 0; step <= 16; step++) {
				const a = step / 16 * Math.PI / 2;
				const x = Math.round(cx + side * span * Math.cos(a));
				const y = Math.round(1 + height * Math.sin(a));
				const axis = step < 8 ? 'y' : 'x';
				for (const dz of [0, 1]) for (const dx of [0, side === 1 ? -1 : 1]) b.set(x + dx, y, z + dz, `minecraft:bone_block[axis=${axis}]`);
			}
		}
		if (i === 2 || i === 6) for (let x = cx - 4; x <= cx + 4; x++) for (let y = 9; y <= 15; y++) for (const dz of [0, 1]) b.del(x, y, z + dz);
	}
	// The hollow of end stone bricks under the ribs, with the chest at the heart.
	for (let x = cx - 6; x <= cx + 6; x++) for (let z = 12; z <= 34; z++) {
		if (Math.hypot(x - cx, (z - 23) * 0.55) <= 6 && !b.get(x, 0, z)) b.set(x, 0, z, 'minecraft:end_stone_bricks');
	}
	// The vault at the heart holds the Aphelite Upgrade template, one per player; its key drops
	// from the phantoms wheeling overhead (see phantomLoot below). A plain chest sits by the spine.
	b.set(cx + 3, 1, 20, 'minecraft:vault[facing=west,ominous=false,vault_state=inactive]', { config: { loot_table: `${NS}:chests/graveyard_vault` } });
	b.set(cx - 3, 1, 26, 'minecraft:chest[facing=east,type=single,waterlogged=false]', { LootTable: `${NS}:chests/graveyard` });
	// Four phantoms, each carrying a key: a vault rewards each player once, so a party needs a
	// key apiece (trial keys from the Overworld's trial chambers open it too).
	b.phantom(cx, 14, 20, `${NS}:entities/graveyard_phantom`);
	b.phantom(cx + 3, 15, 30, `${NS}:entities/graveyard_phantom`);
	b.phantom(cx - 4, 16, 12, `${NS}:entities/graveyard_phantom`);
	b.phantom(cx + 5, 15, 40, `${NS}:entities/graveyard_phantom`);
	// A few scattered bones.
	for (let i = 0; i < 16; i++) {
		const x = 1 + Math.floor(rand() * 27), z = 1 + Math.floor(rand() * 45);
		if (!b.get(x, 1, z) && !b.get(x, 0, z)) b.set(x, 1, z, `minecraft:bone_block[axis=${rand() < 0.5 ? 'x' : 'z'}]`);
	}
	return b;
}

// --- the sinking void city ----------------------------------------------------------------------
// Vanilla's city house (base, second and third floors, roof) with a tower rising from its roof
// and a bridge leaving the tower, laid out with vanilla's own offsets. The east side has slipped
// off the island: walls, floors and the bridge end in broken edges over the void.
function sinkingCity() {
	const b = new Build([44, 48, 74]);
	const rand = rng(23);
	// A city house with a tower on its roof, laid out with vanilla's own offsets (each piece
	// relative to the one before it), with a bridge leaving the tower north (-z) the way
	// vanilla's do. Returns where the bridge ends, so the next cluster can start there.
	const house = (hx, hy, hz, towerPieces, chestLoot) => {
		stamp(b, 'end_city/base_floor', hx, hy, hz);
		stamp(b, 'end_city/second_floor_2', hx - 1, hy, hz - 1);
		stamp(b, 'end_city/third_floor_2', hx - 2, hy + 4, hz - 2, chestLoot);
		stamp(b, 'end_city/third_roof', hx - 3, hy + 12, hz - 3);
		const tx = hx + 1, ty = hy + 9, tz = hz + 1;
		stamp(b, 'end_city/tower_base', tx, ty, tz);
		let y = ty + 7;
		for (let i = 0; i < towerPieces; i++) {
			stamp(b, 'end_city/tower_piece', tx, y, tz);
			y += 4;
		}
		stamp(b, 'end_city/tower_top', tx - 1, y, tz - 1);
		return { tx, tz, bridgeY: y - 4 };
	};
	const bridge = (bx, by, bz, pieces) => {
		let z = bz;
		for (let i = 0; i < pieces; i++) {
			stamp(b, 'end_city/bridge_piece', bx, by, z);
			z -= 4;
		}
		stamp(b, 'end_city/bridge_end', bx, by, z);
		return z;
	};
	// The south cluster: the tallest house, its tower, a bridge north.
	const a = house(16, 3, 58, 3, `${NS}:chests/sinking_city`);
	const aEnd = bridge(a.tx + 1, a.bridgeY, a.tz - 4, 3);
	// The middle: a fat tower where the first bridge lands, with its own bridge on north.
	const fx = a.tx - 5, fy = a.bridgeY - 12, fz = aEnd - 13;
	stamp(b, 'end_city/fat_tower_base', fx, fy, fz);
	stamp(b, 'end_city/fat_tower_middle', fx, fy + 4, fz);
	stamp(b, 'end_city/fat_tower_top', fx - 2, fy + 12, fz - 2);
	const bEnd = bridge(fx + 4, fy + 8, fz - 4, 2);
	// The north cluster: a shorter house that the slip has half taken.
	house(fx + 1, fy - 2, bEnd - 10, 1);
	b.shulker(a.tx + 4, 7, a.tz + 4);
	b.shulker(a.tx - 3, 15, a.tz);
	b.shulker(fx + 6, fy + 13, fz + 6);
	b.shulker(fx + 8, fy + 13, fz + 8);
	b.phantom(fx + 6, fy + 22, fz + 6);
	b.phantom(fx - 2, fy + 20, fz + 12);
	// The slip: everything north-west of a jagged diagonal is gone, more the further out and
	// the lower it is, so the north house, the second bridge and the fat tower's far side are
	// broken off over the void.
	for (const [x, y, z, e] of b.all()) {
		if (e.state === 'minecraft:air') continue;
		const line = 22 + (x - 20) * 0.6 + Math.sin(x * 0.5 + z * 0.3) * 2;
		const over = line - z;
		if (over > 0 && rand() < Math.min(1, over / 7 + Math.max(0, 24 - y) / 40)) b.del(x, y, z);
	}
	return b;
}

/**
 * The build raised on a plinth: end stone `depth` deep under every column whose lowest block is
 * within the bottom few layers (a ship's hull or a tilted city gets a foundation under its whole
 * underside, not just its keel; the city's fat tower, four up, gets a stone column). The structure's start height sinks the template by the same
 * depth, so on flat ground the plinth is buried and on a ragged or sloping island it is the
 * foundation the ruin stands on.
 */
function withPlinth(b, depth) {
	// Empty layers under the build go first, so its lowest block ends up right on the plinth.
	let minY = b.size[1];
	const lowest = new Map();
	for (const [x, y, z, e] of b.all()) {
		if (e.state === 'minecraft:air') continue;
		minY = Math.min(minY, y);
		const k = `${x},${z}`;
		if (!lowest.has(k) || y < lowest.get(k)) lowest.set(k, y);
	}
	const shift = depth - minY;
	const r = new Build([b.size[0], b.size[1] + shift, b.size[2]]);
	for (const [x, y, z, e] of b.all()) if (y + shift >= 0) r.blocks.set(`${x},${y + shift},${z}`, e);
	for (const [k, y0] of lowest) {
		if (y0 - minY > 4) continue;
		for (let d = 0; d < y0 + shift; d++) r.blocks.set(`${k.split(',')[0]},${d},${k.split(',')[1]}`, { state: 'minecraft:end_stone' });
	}
	r.shulkers = b.shulkers.map(([x, y, z]) => [x, y + shift, z]);
	r.phantoms = b.phantoms.map(([x, y, z, loot]) => [x, y + shift, z, loot]);
	return r;
}

// Keep in step with PLINTH in generate-data.py (the structures' start height).
const PLINTH = 8;

function generate(name, intact, missing, title, intro, lectern) {
	const theBook = book(title, intro, missing, intact);
	for (const [, v] of intact.blocks) if (v.nbt && v.nbt.Book) v.nbt.Book = theBook;
	out(`structure/${name}.nbt`, intact.toNbt());
	out(`structure/${name}_ruin.nbt`, intact.ruin(missing).toNbt());
	// What the restoration code checks: each missing block's template position and block id,
	// plus where the lectern is (the respawn spot, and where the chart is read from).
	const plan = {
		size: intact.size,
		lectern,
		missing: missing.filter(([x, y, z]) => intact.get(x, y, z) && intact.get(x, y, z).state !== 'minecraft:air')
			.map(([x, y, z]) => ({ pos: [x, y, z], block: intact.get(x, y, z).state.replace(/\[.*$/, '') })),
	};
	out(`restoration/${name}.json`, JSON.stringify(plan, null, '\t') + '\n');
	console.log(`${name}: ${intact.blocks.size} blocks, ruin missing ${missing.length}`);
}

function generateOne(name, build) {
	out(`structure/${name}.nbt`, build.toNbt());
	console.log(`${name}: ${build.blocks.size} blocks`);
}

const obs = observatory();
obs.build = withPlinth(obs.build, PLINTH);
obs.roof += PLINTH;
obs.floor += PLINTH;
generate('observatory', obs.build, observatoryMissing(obs), 'Keeper\'s Notes',
	'The storm took the roof\'s corner, the east wall and the eyepiece. Put back what is listed here and she will see again.\n\nThe watch ends when the last piece is set.',
	[10 - 2, obs.floor, 10 + 3]);
generateOne('wreck', withPlinth(wreck(), PLINTH));
generateOne('graveyard', withPlinth(graveyard(), PLINTH));
generateOne('sinking_city', withPlinth(sinkingCity(), PLINTH));
