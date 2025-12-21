# Infuse Spark (Paper 1.21.x)

Standalone Paper plugin conversion of the original Infuse Skript. This plugin keeps the spark items, abilities, cooldowns, and the bundled resource pack.

## Features
- Primary and support spark items with the same recipes and lore.
- Ability activations with the same cooldowns, sounds, and action bar icons.
- Support for `/infuse`, `/infusespark`, `/pdrain`, and `/sdrain` commands.
- Built-in HTTP server to serve the bundled resource pack.

## Commands
- `/infusespark equip <primary|support> <type>`
- `/infusespark cd_reset`
- `/infuse settings control_set <offhand|crouch_mouseclicks|custom_keys>`
- `/infuse primary <strength|heart|haste|invisibility|feather|frost|thunder|regeneration|empty>`
- `/infuse support <ocean|fire|emerald|speed|empty>`
- `/infuse secondary <ocean|fire|emerald|speed|empty>` (alias)
- `/infuse ability <primary|support>`
- `/infuse trust add <player>`
- `/infuse trust remove <player|all>`
- `/pdrain`
- `/sdrain`

## Resource Pack
The plugin serves a resource pack from the plugin data folder. Copy `Infuse Pack.zip` into
`plugins/Infuse/` (same folder as `config.yml`) and the plugin will host it over HTTP.

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
mvn clean package test
```

The plugin JAR will be generated in `target/`.
