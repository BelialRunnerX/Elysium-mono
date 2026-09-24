# The vanilla enchantments, as they ship

These forty-two files are Minecraft 1.21.1's own `data/minecraft/enchantment/`
tree, copied here unchanged. They are the *input* to `gen_enchanting.py`, not
output: the generator reads one, applies the row the ladder table gives for it,
and writes the result into `src/main/resources/data/minecraft/enchantment/`.

They are vendored rather than read out of a game jar so that the generator runs
anywhere, and so that `validate.py` can check that everything the ladder did
not set out to change — the description, the effects, the slots, what the
enchantment may go on — came through untouched. A mod that raises Sharpness to
ten and quietly changes what Sharpness does has made a change nobody asked for
and nobody was told about.

Update them when the game updates, and re-run the generator. The diff of the
shipped tree is then exactly what this mod did to that version.
