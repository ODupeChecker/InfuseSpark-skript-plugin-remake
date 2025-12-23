# Infuse Spark (Paper 1.21.x)

Standalone Paper plugin conversion of the original Infuse Skript. This plugin keeps the spark items, abilities, cooldowns, and the bundled resource pack.

## Features
- Primary and support spark items with the same recipes and lore.
- Ability activations with the same cooldowns, sounds, and action bar icons.
- Support for `/infuse` and `/drain` commands.
- Built-in HTTP server to serve the resource pack.
- Adds The Piglin Effect primary infuse (mark windows and bloodmark sparks).

## Commands
- `/infuse spark equip effect <type> <1|2>`
  - Types: `strength`, `heart`, `haste`, `invisibility`, `feather`, `frost`, `thunder`, `regeneration`, `piglin`,
    `ocean`, `fire`, `emerald`, `speed`
- `/infuse spark cd_reset`
- `/infuse settings control_set <offhand|crouch_mouseclicks|ari_keys>`
- `/infuse primary` (activate primary ability)
- `/infuse support` (activate support ability)
- `/infuse ability <primary|support>`
- `/infuse trust add <player>`
- `/infuse trust remove <player|all>`
- `/drain 1` (drain primary)
- `/drain 2` (drain support)

## Resource Pack
The plugin serves a resource pack from the plugin data folder. To get the texture pack:
1. Use the original `Infuse Pack.zip` from this repository root (next to `README.md`), **or**
2. Use the same `Infuse Pack.zip` you already had in your previous setup.

Then copy `Infuse Pack.zip` into `plugins/Infuse/` (same folder as `config.yml`) and the plugin will host it over HTTP.

### Updating the resource pack for The Piglin Effect
To show The Piglin Effect icon on the action bar, update the resource pack:
1. Open `Infuse Pack.zip` and locate `assets/minecraft/font/default.json`.
2. In the `providers` list (custom textures section), add entries for the piglin textures alongside the existing entries like `emerald.png` and `emeraldspark.png`.
3. Place new texture files in the same folder as the other infuse textures:
   - `piglin.png` (inactive icon)
   - `piglinspark.png` (active icon)

Use the same format as the other abilities, but assign The Piglin Effect glyphs to the next available codes:
- `\uE026` for `piglin.png`
- `\uE027` for `piglinspark.png`

Configure `config.yml`:
```yaml
resource-pack:
  enable: true
  public-url: "" # Optional: set to a public URL if your server IP is not discoverable.
  port: 8173
  path: "Infuse Pack.zip"
```
If `public-url` is left blank, the plugin builds the URL from your server IP and the configured port.

## Build
```bash
mvn clean package
```

The plugin JAR will be generated in `target/`.
