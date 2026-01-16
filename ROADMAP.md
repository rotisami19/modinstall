# 🗺️ Roadmap v2.0 - ModInstall

Goal: Make ModInstall more versatile, robust, and support multiple sources.

## 🚀 Core Features

### 1. CurseForge Support (`--source curseforge`) 🐺
- **Description**: Add the ability to install mods from CurseForge.
- **Challenges**: CurseForge API requires an API Key (or legal workaround). File formats differ.
- **Command**: `modinstall install --source cf jeust`

### 2. `update` Command (Global or Targeted) 🆙
- **Description**: Checks if newer compatible versions exist for installed mods.
- **Features**:
  - **Global Update**: Updates *all* mods in the folder (`modinstall update`).
  - **Targeted Update**: Updates *only* the specified mod (`modinstall update create`).
  - Automatically handles new dependencies during updates.
- **Option**: `--dry-run` to see pending changes without applying them.

### 3. Lightweight "Modpacks" Management (Load/Save) 📦
- **Description**: Save the current list of mods to a file (e.g., `modlist.json`) and reinstall the same list elsewhere.
- **Commands**:
  - `modinstall export package.json`
  - `modinstall import package.json`

### 4. Self-Update Tool 🔄
- **Description**: The tool can update itself directly from GitHub Releases.
- **Command**: `modinstall upgrade`

## 🛠️ Technical Improvements

- **Multi-Loader Detection**: Better handling of ambiguous cases (e.g., Quilt vs Fabric).
- **Download Cache**: Avoid re-downloading a dependency if it's already in a temporary cache.
- **Colors & UI**: Further improvements to progress bars and error messages.

## 🎨 Bonus Ideas (To Be Confirmed)

- **GUI (Graphical User Interface)**: A simple window to search/click/install for users who dislike CLI.
- **Config Editor**: Ability to change simple config options via CLI?

---
*Living document - Subject to change based on needs.*
