# Light Suppression Wand
Shift+right-click any light-emitting block with a golden hoe to toggle its light emission off/on. No client mod required.

## Usage
1. Hold a **golden hoe**
2. **Shift+right-click** a light-emitting block (glowstone, torches, lanterns, etc.)
3. The block's light is suppressed — the block stays but emits no light
4. **Shift+right-click** again to restore the light

## Features
- Works with any light-emitting block (torches, glowstone, lanterns, nether portals, etc.)
- Suppression persists across server restarts
- Per-dimension tracking — each world stores its own suppression data
- Automatic cleanup when suppressed blocks are removed by any means (player breaking, block updates, explosions, pistons, etc.)

## Building
Requires Java 21.

```sh
./gradlew build
```

The built jar will be at `build/libs/.