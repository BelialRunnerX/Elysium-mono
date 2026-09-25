# The three world presets that use vanilla's overworld list

Taken verbatim out of the game's own jar, from
`data/minecraft/worldgen/world_preset/`. These are the only three of the seven
vanilla ships whose overworld says `"preset": "minecraft:overworld"` — the
other four are flat, debug and single-biome worlds and have no list to add to.

The generator reads each of them, replaces the overworld's `biome_source` with
the explicit list — ours, then vanilla's 7,593 entries — and writes the result
into `data/minecraft/worldgen/world_preset/`. Everything else in the file goes
through untouched, which is what `validate.py` checks: the nether and the end
in our copy have to be byte for byte what they are here, or we have quietly
rewritten two dimensions nobody asked us to touch.
