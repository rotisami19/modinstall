# 🗺️ Roadmap v2.0 - ModInstall

Objectif : Rendre ModInstall plus polyvalent, plus robuste et supporter plus de sources.

## 🚀 Fonctionnalités Principales (Core Features)

### 1. Support CurseForge (`--source curseforge`) 🐺
- **Description** : Ajouter la possibilité d'installer des mods depuis CurseForge.
- **Défis** : L'API CurseForge nécessite une clé API (ou un contournement légal). Le format des fichiers est différent.
- **Commande** : `modinstall install --source cf jeust`

### 2. Commande `update` (Mise à jour Globale ou Ciblée) 🆙
- **Description** : Vérifie si des versions plus récentes compatibles existent.
- **Fonctionnalités** :
  - **Update Global** : Met à jour *tous* les mods du dossier (`modinstall update`).
  - **Update Ciblé** : Met à jour *uniquement* le mod spécifié (`modinstall update create`).
  - Gestion automatique des nouvelles dépendances lors de la mise à jour.
- **Option** : `--dry-run` pour voir ce qui va changer sans rien faire.

### 3. Gestion de "Modpacks" légers (Load/Save) 📦
- **Description** : Permettre de sauvegarder la liste des mods actuels dans un fichier (ex: `modlist.json`) et de réinstaller la même liste ailleurs.
- **Commandes** :
  - `modinstall export package.json`
  - `modinstall import package.json`

### 4. Auto-Update de l'outil (Self-Update) 🔄
- **Description** : L'outil peut se mettre à jour lui-même depuis GitHub Releases.
- **Commande** : `modinstall upgrade`

## 🛠️ Améliorations Techniques

- **Détection Multi-Loader** : Mieux gérer les cas ambigus (ex: Quilt vs Fabric).
- **Cache de téléchargement** : Ne pas retélécharger une dépendance si elle est déjà dans un dossier cache temporaire.
- **Colors & UI** : Améliorer encore les barres de progression et les messages d'erreur.

## 🎨 Idées Bonus (À confirmer)

- **GUI (Interface Graphique)** : Une petite interface simple pour chercher/cliquer/installer.
- **Config Editor** : Possibilité de changer des options de config simples via CLI ?

---
*Document de travail - Modifiable selon les besoins.*
