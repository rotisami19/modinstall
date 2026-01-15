package dev.modinstall;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * ModInstall - Stylish CLI tool to install Minecraft mods from Modrinth
 * Auto-detects loader and MC version from gradle.properties
 */
public class ModInstall {
    
    private static final String MODRINTH_API = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "ModInstall/1.0.0 (github.com/modinstall)";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // ANSI Color Codes
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    
    // Colors
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";
    
    // Bright colors
    private static final String BRIGHT_RED = "\u001B[91m";
    private static final String BRIGHT_GREEN = "\u001B[92m";
    private static final String BRIGHT_YELLOW = "\u001B[93m";
    private static final String BRIGHT_BLUE = "\u001B[94m";
    private static final String BRIGHT_MAGENTA = "\u001B[95m";
    private static final String BRIGHT_CYAN = "\u001B[96m";
    
    // Background colors
    private static final String BG_BLUE = "\u001B[44m";
    private static final String BG_MAGENTA = "\u001B[45m";
    
    // Box drawing characters - with ASCII fallback
    private static String BOX_TL, BOX_TR, BOX_BL, BOX_BR, BOX_H, BOX_V, BOX_LT, BOX_RT;
    private static String BULLET, CHECK, CROSS, WARN, ARROW;
    private static boolean useUnicode;
    
    static {
        // Check if terminal supports Unicode
        useUnicode = checkUnicodeSupport();
        if (useUnicode) {
            BOX_TL = "┌"; BOX_TR = "┐"; BOX_BL = "└"; BOX_BR = "┘";
            BOX_H = "─"; BOX_V = "│"; BOX_LT = "├"; BOX_RT = "┤";
            BULLET = "●"; CHECK = "✓"; CROSS = "✗"; WARN = "!"; ARROW = "→";
        } else {
            BOX_TL = "+"; BOX_TR = "+"; BOX_BL = "+"; BOX_BR = "+";
            BOX_H = "-"; BOX_V = "|"; BOX_LT = "+"; BOX_RT = "+";
            BULLET = "*"; CHECK = "+"; CROSS = "x"; WARN = "!"; ARROW = ">";
        }
    }
    
    private static boolean checkUnicodeSupport() {
        // Check console encoding
        String encoding = System.getProperty("stdout.encoding", 
            System.getProperty("sun.stdout.encoding", 
                System.getProperty("file.encoding", "UTF-8")));
        
        // Windows Terminal and modern consoles support Unicode
        String term = System.getenv("WT_SESSION"); // Windows Terminal
        String termProgram = System.getenv("TERM_PROGRAM");
        
        if (term != null || "vscode".equals(termProgram)) {
            return true;
        }
        
        // Check if running in ConEmu, Cmder, etc.
        if (System.getenv("ConEmuANSI") != null) {
            return true;
        }
        
        return encoding.toUpperCase().contains("UTF");
    }
    
    private String minecraftVersion;
    private String loader;
    private Path modsFolder;
    private Path projectRoot;
    
    public static void main(String[] args) {
        // Enable ANSI on Windows
        enableAnsiWindows();
        
        if (args.length == 0) {
            printBanner();
            printHelp();
            return;
        }
        
        String command = args[0].toLowerCase();
        
        // Handle help immediately without requiring project structure
        if (command.equals("help") || command.equals("-h") || command.equals("--help")) {
            printBanner();
            printHelp();
            return;
        }
        
        ModInstall installer = new ModInstall();
        
        try {
            installer.detectProject();
            
            switch (command) {
                case "install", "i", "add" -> {
                    if (args.length < 2) {
                        error("Usage: modinstall install <mod-name> [mod-name2] ...");
                        return;
                    }
                    for (int i = 1; i < args.length; i++) {
                        installer.installMod(args[i]);
                        if (i < args.length - 1) System.out.println();
                    }
                }
                case "search", "s", "find" -> {
                    if (args.length < 2) {
                        error("Usage: modinstall search <query>");
                        return;
                    }
                    installer.searchMods(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                }
                case "list", "l", "ls" -> installer.listInstalled();
                case "remove", "r", "rm", "uninstall" -> {
                    if (args.length < 2) {
                        error("Usage: modinstall remove <mod-name>");
                        return;
                    }
                    installer.removeMod(args[1]);
                }
                case "clean" -> installer.cleanUnused();
                case "info", "status" -> installer.showProjectInfo();
                default -> {
                    error("Unknown command: " + args[0]);
                    printHelp();
                }
            }
        } catch (Exception e) {
            error(e.getMessage());
        }
    }
    
    private static void enableAnsiWindows() {
        // Try to enable ANSI on Windows 10+
        try {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                new ProcessBuilder("cmd", "/c", "chcp 65001 > nul").inheritIO().start().waitFor();
            }
        } catch (Exception ignored) {}
    }
    
    private static void printBanner() {
        System.out.println();
        if (useUnicode) {
            System.out.println(BRIGHT_MAGENTA + "    ╔═══════════════════════════════════════════════════════════════╗" + RESET);
            System.out.println(BRIGHT_MAGENTA + "    ║" + RESET + BOLD + BRIGHT_CYAN + "  ███╗   ███╗ ██████╗ ██████╗ ██╗███╗   ██╗███████╗████████╗   " + RESET + BRIGHT_MAGENTA + "║" + RESET);
            System.out.println(BRIGHT_MAGENTA + "    ║" + RESET + BOLD + BRIGHT_CYAN + "  ████╗ ████║██╔═══██╗██╔══██╗██║████╗  ██║██╔════╝╚══██╔══╝   " + RESET + BRIGHT_MAGENTA + "║" + RESET);
            System.out.println(BRIGHT_MAGENTA + "    ║" + RESET + BOLD + BRIGHT_CYAN + "  ██╔████╔██║██║   ██║██║  ██║██║██╔██╗ ██║███████╗   ██║      " + RESET + BRIGHT_MAGENTA + "║" + RESET);
            System.out.println(BRIGHT_MAGENTA + "    ║" + RESET + BOLD + BRIGHT_CYAN + "  ██║╚██╔╝██║██║   ██║██║  ██║██║██║╚██╗██║╚════██║   ██║      " + RESET + BRIGHT_MAGENTA + "║" + RESET);
            System.out.println(BRIGHT_MAGENTA + "    ║" + RESET + BOLD + BRIGHT_CYAN + "  ██║ ╚═╝ ██║╚██████╔╝██████╔╝██║██║ ╚████║███████║   ██║      " + RESET + BRIGHT_MAGENTA + "║" + RESET);
            System.out.println(BRIGHT_MAGENTA + "    ║" + RESET + BOLD + BRIGHT_CYAN + "  ╚═╝     ╚═╝ ╚═════╝ ╚═════╝ ╚═╝╚═╝  ╚═══╝╚══════╝   ╚═╝      " + RESET + BRIGHT_MAGENTA + "║" + RESET);
            System.out.println(BRIGHT_MAGENTA + "    ╠═══════════════════════════════════════════════════════════════╣" + RESET);
            System.out.println(BRIGHT_MAGENTA + "    ║" + RESET + DIM + "         Minecraft Mod Installer - Powered by Modrinth         " + RESET + BRIGHT_MAGENTA + "║" + RESET);
            System.out.println(BRIGHT_MAGENTA + "    ╚═══════════════════════════════════════════════════════════════╝" + RESET);
        } else {
            // ASCII fallback
            System.out.println(BRIGHT_MAGENTA + "    +===================================================================+" + RESET);
            System.out.println(BRIGHT_MAGENTA + "    |" + RESET + BOLD + BRIGHT_CYAN + "   __  __  ___  ___  ___ _  _  ___ _____ _   _    _    " + RESET + BRIGHT_MAGENTA + "          |" + RESET);
            System.out.println(BRIGHT_MAGENTA + "    |" + RESET + BOLD + BRIGHT_CYAN + "  |  \\/  |/ _ \\|   \\|_ _| \\| |/ __|_   _/_\\ | |  | |   " + RESET + BRIGHT_MAGENTA + "          |" + RESET);
            System.out.println(BRIGHT_MAGENTA + "    |" + RESET + BOLD + BRIGHT_CYAN + "  | |\\/| | (_) | |) || || .` |\\__ \\ | |/ _ \\| |__| |__ " + RESET + BRIGHT_MAGENTA + "          |" + RESET);
            System.out.println(BRIGHT_MAGENTA + "    |" + RESET + BOLD + BRIGHT_CYAN + "  |_|  |_|\\___/|___/|___|_|\\_||___/ |_/_/ \\_\\____|____|" + RESET + BRIGHT_MAGENTA + "          |" + RESET);
            System.out.println(BRIGHT_MAGENTA + "    +===================================================================+" + RESET);
            System.out.println(BRIGHT_MAGENTA + "    |" + RESET + DIM + "         Minecraft Mod Installer - Powered by Modrinth         " + RESET + BRIGHT_MAGENTA + "|" + RESET);
            System.out.println(BRIGHT_MAGENTA + "    +===================================================================+" + RESET);
        }
        System.out.println();
    }
    
    private static void printHelp() {
        String line = useUnicode ? repeat("─", 55) : repeat("-", 55);
        
        System.out.println(BOLD + WHITE + "  USAGE" + RESET);
        System.out.println(DIM + "  " + line + RESET);
        System.out.println("    " + BRIGHT_CYAN + "modinstall" + RESET + " " + YELLOW + "<command>" + RESET + " " + DIM + "[arguments]" + RESET);
        System.out.println();
        
        System.out.println(BOLD + WHITE + "  COMMANDS" + RESET);
        System.out.println(DIM + "  " + line + RESET);
        System.out.println("    " + BRIGHT_GREEN + "install" + RESET + ", " + DIM + "i, add" + RESET + "    " + WHITE + "<mod> [mod2...]" + RESET + "  Install mod(s)");
        System.out.println("    " + BRIGHT_YELLOW + "search" + RESET + ", " + DIM + "s, find" + RESET + "   " + WHITE + "<query>" + RESET + "         Search for mods");
        System.out.println("    " + BRIGHT_BLUE + "list" + RESET + ", " + DIM + "l, ls" + RESET + "                        List installed mods");
        System.out.println("    " + BRIGHT_RED + "remove" + RESET + ", " + DIM + "r, rm" + RESET + "     " + WHITE + "<mod>" + RESET + "           Remove a mod");
        System.out.println("    " + BRIGHT_MAGENTA + "clean" + RESET + "                             Remove unused libraries");
        System.out.println("    " + BRIGHT_MAGENTA + "info" + RESET + ", " + DIM + "status" + RESET + "                      Show project info");
        System.out.println();
        
        System.out.println(BOLD + WHITE + "  EXAMPLES" + RESET);
        System.out.println(DIM + "  " + line + RESET);
        System.out.println("    " + DIM + "$" + RESET + " modinstall " + BRIGHT_GREEN + "install" + RESET + " jei");
        System.out.println("    " + DIM + "$" + RESET + " modinstall " + BRIGHT_GREEN + "i" + RESET + " jei jade create");
        System.out.println("    " + DIM + "$" + RESET + " modinstall " + BRIGHT_YELLOW + "search" + RESET + " optimization");
        System.out.println("    " + DIM + "$" + RESET + " modinstall " + BRIGHT_BLUE + "list" + RESET);
        System.out.println();
    }
    
    // === Styled output helpers ===
    
    private static void info(String msg) {
        System.out.println("  " + BRIGHT_CYAN + "[i]" + RESET + " " + msg);
    }
    
    private static void success(String msg) {
        System.out.println("  " + BRIGHT_GREEN + CHECK + RESET + "  " + msg);
    }
    
    private static void warning(String msg) {
        System.out.println("  " + BRIGHT_YELLOW + WARN + RESET + "  " + YELLOW + msg + RESET);
    }
    
    private static void error(String msg) {
        System.out.println("  " + BRIGHT_RED + CROSS + RESET + "  " + RED + msg + RESET);
    }
    
    private static void step(String msg) {
        System.out.println("  " + BRIGHT_MAGENTA + ARROW + RESET + "  " + msg);
    }
    
    private static void bullet(String msg) {
        System.out.println("     " + DIM + BULLET + RESET + " " + msg);
    }
    
    /**
     * Detect project info from gradle.properties
     */
    private void detectProject() throws IOException {
        projectRoot = findProjectRoot();
        
        if (projectRoot == null) {
            throw new IOException("Could not find gradle.properties. Run this from a Minecraft mod project directory.");
        }
        
        Path gradleProps = projectRoot.resolve("gradle.properties");
        Properties props = new Properties();
        
        try (InputStream is = Files.newInputStream(gradleProps)) {
            props.load(is);
        }
        
        // Detect Minecraft version
        minecraftVersion = props.getProperty("minecraft_version");
        if (minecraftVersion == null) {
            minecraftVersion = props.getProperty("mc_version");
        }
        if (minecraftVersion == null) {
            throw new IOException("Could not detect minecraft_version from gradle.properties");
        }
        
        // Detect loader
        loader = detectLoader(props, projectRoot);
        
        // Find mods folder (run/mods)
        modsFolder = projectRoot.resolve("run").resolve("mods");
        Files.createDirectories(modsFolder);
        
        // Print detection info - fixed width box
        int boxWidth = 48;
        String mcValue = minecraftVersion;
        String loaderValue = capitalize(loader);
        String modsValue = shortenPath(modsFolder.toString(), 26);
        String hLine = repeat(BOX_H, boxWidth);
        
        System.out.println();
        System.out.println("  " + DIM + BOX_TL + hLine + BOX_TR + RESET);
        System.out.println("  " + DIM + BOX_V + RESET + BOLD + "  Project Detected" + RESET + repeat(" ", boxWidth - 18) + DIM + BOX_V + RESET);
        System.out.println("  " + DIM + BOX_LT + hLine + BOX_RT + RESET);
        System.out.println(formatBoxRow(CYAN, BULLET, "Minecraft", BRIGHT_GREEN + BOLD + mcValue + RESET, boxWidth));
        System.out.println(formatBoxRow(YELLOW, BULLET, "Loader", BRIGHT_YELLOW + BOLD + loaderValue + RESET, boxWidth));
        System.out.println(formatBoxRow(BLUE, BULLET, "Mods", DIM + modsValue + RESET, boxWidth));
        System.out.println("  " + DIM + BOX_BL + hLine + BOX_BR + RESET);
        System.out.println();
    }
    
    /**
     * Find project root by looking for gradle.properties
     */
    private Path findProjectRoot() {
        Path current = Paths.get(".").toAbsolutePath().normalize();
        
        while (current != null) {
            if (Files.exists(current.resolve("gradle.properties"))) {
                return current;
            }
            current = current.getParent();
        }
        
        return null;
    }
    
    /**
     * Detect which loader is being used
     */
    private String detectLoader(Properties props, Path root) {
        // Check for NeoForge
        if (props.getProperty("neoforge_version") != null || 
            props.getProperty("neo_version") != null ||
            Files.exists(root.resolve("NeoForge"))) {
            return "neoforge";
        }
        
        // Check for Fabric
        if (props.getProperty("fabric_version") != null ||
            props.getProperty("fabric_loader_version") != null ||
            Files.exists(root.resolve("Fabric"))) {
            return "fabric";
        }
        
        // Check for Forge
        if (props.getProperty("forge_version") != null ||
            Files.exists(root.resolve("Forge"))) {
            return "forge";
        }
        
        // Check for Quilt
        if (props.getProperty("quilt_version") != null) {
            return "quilt";
        }
        
        return "forge"; // Default
    }
    
    /**
     * Install a mod from Modrinth
     */
    private void installMod(String modSlug) throws IOException {
        step("Searching for " + BOLD + modSlug + RESET + "...");
        
        // Search for the mod (Force project_type:mod)
        String facets = "[[\"categories:" + loader + "\"],[\"versions:" + minecraftVersion + "\"],[\"project_type:mod\"]]";
        String searchUrl = MODRINTH_API + "/search?query=" + URLEncoder.encode(modSlug, "UTF-8") 
            + "&facets=" + URLEncoder.encode(facets, "UTF-8")
            + "&limit=5";
        
        JsonObject searchResult = httpGetJson(searchUrl);
        JsonArray hits = searchResult.getAsJsonArray("hits");
        
        if (hits.isEmpty()) {
            error("No mod found for '" + modSlug + "' on " + loader + " " + minecraftVersion);
            return;
        }
        
        // Get the first matching mod
        JsonObject mod = hits.get(0).getAsJsonObject();
        String projectId = mod.get("project_id").getAsString();
        String title = mod.get("title").getAsString();
        String slug = mod.get("slug").getAsString();
        
        info("Found: " + BOLD + BRIGHT_CYAN + title + RESET + DIM + " (" + slug + ")" + RESET);
        
        // Get versions for this mod
        String loaders = "[\"" + loader + "\"]";
        String gameVersions = "[\"" + minecraftVersion + "\"]";
        String versionsUrl = MODRINTH_API + "/project/" + projectId + "/version"
            + "?loaders=" + URLEncoder.encode(loaders, "UTF-8")
            + "&game_versions=" + URLEncoder.encode(gameVersions, "UTF-8");
        
        JsonArray versions = httpGetJsonArray(versionsUrl);
        
        if (versions.isEmpty()) {
            error("No compatible version found for " + loader + " " + minecraftVersion);
            return;
        }
        
        // Get the latest version
        JsonObject version = versions.get(0).getAsJsonObject();
        JsonArray files = version.getAsJsonArray("files");
        
        // Find primary file
        JsonObject primaryFile = null;
        for (JsonElement fileEl : files) {
            JsonObject file = fileEl.getAsJsonObject();
            if (file.get("primary").getAsBoolean()) {
                primaryFile = file;
                break;
            }
        }
        if (primaryFile == null && !files.isEmpty()) {
            primaryFile = files.get(0).getAsJsonObject();
        }
        
        if (primaryFile == null) {
            error("No downloadable file found");
            return;
        }
        
        String downloadUrl = primaryFile.get("url").getAsString();
        String fileName = primaryFile.get("filename").getAsString();
        String versionNumber = version.get("version_number").getAsString();
        long fileSize = primaryFile.get("size").getAsLong();
        
        // Define target file
        Path targetFile = modsFolder.resolve(fileName);
        
        // 1. Check if already installed
        if (Files.exists(targetFile)) {
            warning("Already installed: " + fileName);
            return;
        }
        
        // 2. Install dependencies FIRST
        JsonArray dependencies = version.getAsJsonArray("dependencies");
        if (dependencies != null && !dependencies.isEmpty()) {
            boolean hasDeps = false;
            for (JsonElement depEl : dependencies) {
                JsonObject dep = depEl.getAsJsonObject();
                String depType = dep.get("dependency_type").getAsString();
                if ("required".equals(depType)) {
                    String depProjectId = dep.has("project_id") ? dep.get("project_id").getAsString() : null;
                    if (depProjectId != null) {
                        if (!hasDeps) {
                            System.out.println();
                            info("Installing required dependencies...");
                            hasDeps = true;
                        }
                        try {
                            JsonObject depProject = httpGetJson(MODRINTH_API + "/project/" + depProjectId);
                            String depSlug = depProject.get("slug").getAsString();
                            System.out.println();
                            installMod(depSlug);
                        } catch (Exception e) {
                            warning("Could not resolve dependency: " + depProjectId);
                        }
                    }
                }
            }
            if (hasDeps) {
                System.out.println();
                info("Now installing " + BOLD + title + RESET + "...");
            }
        }
        
        // 3. Download main mod
        step("Downloading " + BOLD + fileName + RESET + DIM + " (" + formatSize(fileSize) + ")" + RESET);
        downloadFileWithProgress(downloadUrl, targetFile, fileSize);
        
        success(BOLD + title + RESET + " " + GREEN + "v" + versionNumber + RESET + " installed!");
    }
    
    /**
     * Download file with progress bar
     */
    private void downloadFileWithProgress(String urlString, Path target, long expectedSize) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        
        try (InputStream in = conn.getInputStream();
             OutputStream out = Files.newOutputStream(target)) {
            
            byte[] buffer = new byte[8192];
            long downloaded = 0;
            int bytesRead;
            int lastPercent = -1;
            
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                downloaded += bytesRead;
                
                int percent = (int) ((downloaded * 100) / expectedSize);
                if (percent != lastPercent && percent % 5 == 0) {
                    printProgressBar(percent, downloaded, expectedSize);
                    lastPercent = percent;
                }
            }
            
            // Final progress
            printProgressBar(100, expectedSize, expectedSize);
            System.out.println();
        }
    }
    
    private void printProgressBar(int percent, long downloaded, long total) {
        int barWidth = 30;
        int filled = (int) ((percent / 100.0) * barWidth);
        
        // Characters for progress bar
        String filledChar = useUnicode ? "█" : "#";
        String partialChar = useUnicode ? "▓" : "=";
        String emptyChar = useUnicode ? "░" : ".";
        
        StringBuilder bar = new StringBuilder();
        bar.append("\r     ");
        bar.append(BRIGHT_MAGENTA).append("[");
        
        for (int i = 0; i < barWidth; i++) {
            if (i < filled) {
                bar.append(BRIGHT_CYAN).append(filledChar);
            } else if (i == filled) {
                bar.append(BRIGHT_CYAN).append(partialChar);
            } else {
                bar.append(DIM).append(emptyChar);
            }
        }
        
        bar.append(BRIGHT_MAGENTA).append("]").append(RESET);
        bar.append(String.format(" %3d%% ", percent));
        bar.append(DIM).append(formatSize(downloaded)).append("/").append(formatSize(total)).append(RESET);
        
        System.out.print(bar);
    }
    
    /**
     * Search for mods on Modrinth
     */
    private void searchMods(String query) throws IOException {
        step("Searching for " + BOLD + "\"" + query + "\"" + RESET + "...");
        System.out.println();
        
        String facets = "[[\"categories:" + loader + "\"],[\"versions:" + minecraftVersion + "\"],[\"project_type:mod\"]]";
        String searchUrl = MODRINTH_API + "/search?query=" + URLEncoder.encode(query, "UTF-8")
            + "&facets=" + URLEncoder.encode(facets, "UTF-8")
            + "&limit=10";
        
        JsonObject result = httpGetJson(searchUrl);
        JsonArray hits = result.getAsJsonArray("hits");
        
        if (hits.isEmpty()) {
            error("No mods found for '" + query + "'");
            return;
        }
        
        int total = result.get("total_hits").getAsInt();
        System.out.println("  " + DIM + "Found " + RESET + BOLD + total + RESET + DIM + " results (showing top " + hits.size() + ")" + RESET);
        System.out.println();
        
        for (int i = 0; i < hits.size(); i++) {
            JsonObject hit = hits.get(i).getAsJsonObject();
            String title = hit.get("title").getAsString();
            String slug = hit.get("slug").getAsString();
            String description = hit.get("description").getAsString();
            int downloads = hit.get("downloads").getAsInt();
            
            System.out.println("  " + BRIGHT_MAGENTA + (i + 1) + "." + RESET + " " + BOLD + BRIGHT_CYAN + title + RESET + "  " + DIM + "(" + slug + ")" + RESET);
            System.out.println("     " + DIM + truncate(description, 55) + RESET);
            System.out.println("     " + formatDownloadsBadge(downloads));
            System.out.println();
        }
        
        System.out.println("  " + DIM + repeat(useUnicode ? "─" : "-", 55) + RESET);
        System.out.println("  " + BRIGHT_YELLOW + "Tip:" + RESET + " Install with: " + BRIGHT_CYAN + "modinstall install " + YELLOW + "<slug>" + RESET);
        System.out.println();
    }
    
    private String formatDownloadsBadge(int downloads) {
        String formatted = formatDownloads(downloads);
        String color;
        
        if (downloads >= 1_000_000) {
            color = BRIGHT_GREEN;
        } else if (downloads >= 100_000) {
            color = BRIGHT_CYAN;
        } else if (downloads >= 10_000) {
            color = BRIGHT_YELLOW;
        } else {
            color = DIM;
        }
        
        String icon = useUnicode ? "↓" : "v";
        return color + icon + " " + formatted + " downloads" + RESET;
    }
    
    /**
     * List installed mods
     */
    private void listInstalled() throws IOException {
        if (!Files.exists(modsFolder)) {
            warning("Mods folder doesn't exist yet.");
            return;
        }
        
        List<Path> mods = Files.list(modsFolder)
            .filter(p -> p.toString().endsWith(".jar"))
            .sorted()
            .toList();
        
        if (mods.isEmpty()) {
            info("No mods installed in " + modsFolder);
            return;
        }
        
        System.out.println("  " + BOLD + WHITE + "Installed Mods" + RESET + DIM + " (" + mods.size() + ")" + RESET);
        System.out.println("  " + DIM + repeat(useUnicode ? "─" : "-", 55) + RESET);
        System.out.println();
        
        long totalSize = 0;
        for (Path mod : mods) {
            long size = Files.size(mod);
            totalSize += size;
            String name = mod.getFileName().toString();
            
            // Try to extract mod name (before version number)
            String displayName = extractModName(name);
            
            String icon = useUnicode ? "■" : "*";
            System.out.println("    " + BRIGHT_CYAN + icon + RESET + " " + BOLD + displayName + RESET);
            System.out.println("       " + DIM + name + RESET);
            System.out.println("       " + DIM + "Size: " + formatSize(size) + RESET);
            System.out.println();
        }
        
        System.out.println("  " + DIM + repeat(useUnicode ? "─" : "-", 55) + RESET);
        System.out.println("  " + DIM + "Total: " + RESET + BOLD + mods.size() + RESET + DIM + " mods, " + formatSize(totalSize) + RESET);
        System.out.println();
    }
    
    private String extractModName(String filename) {
        // Remove .jar
        String name = filename.replaceAll("\\.jar$", "");
        // Try to split on version patterns
        String[] parts = name.split("-(?=\\d+\\.\\d+)");
        if (parts.length > 0) {
            return parts[0].replace("-", " ").replace("_", " ");
        }
        return name;
    }
    
    /**
     * Remove a mod
     */
    private void removeMod(String modName) throws IOException {
        List<Path> matches = Files.list(modsFolder)
            .filter(p -> p.getFileName().toString().toLowerCase().contains(modName.toLowerCase()))
            .toList();
            
        if (matches.isEmpty()) {
            error("No mod found matching '" + modName + "'");
            return;
        }
        
        if (matches.size() > 1) {
            warning("Multiple mods found:");
            for (Path p : matches) {
                System.out.println("  - " + p.getFileName());
            }
            warning("Please be more specific.");
            return;
        }
        
        Path targetMod = matches.get(0);
        Set<String> dependencies = getDependenciesFromJar(targetMod);
        
        // List of files to remove: starts with the target mod
        List<Path> toRemove = new ArrayList<>();
        toRemove.add(targetMod);
        
        // Check finding orphans
        if (!dependencies.isEmpty()) {
            info("Checking for unused dependencies...");
            
            // Get all OTHER mods (excluding the target)
            List<Path> otherMods = Files.list(modsFolder)
                .filter(p -> !p.equals(targetMod) && p.toString().endsWith(".jar"))
                .toList();
            
            // For each dependency of the target, check if it is used by any OTHER mod
            for (String depId : dependencies) {
                boolean isUsed = false;
                for (Path other : otherMods) {
                    Set<String> otherDeps = getDependenciesFromJar(other);
                    if (otherDeps.contains(depId)) {
                        isUsed = true;
                        break;
                    }
                }
                
                if (!isUsed) {
                    // Orphan detected! Find the JAR file corresponding to this depId
                    // Heuristic: file name contains depId
                    Optional<Path> orphanJar = Files.list(modsFolder)
                        .filter(p -> !p.equals(targetMod)) // Don't re-add target
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            String simpleId = depId.toLowerCase().replace("_", "-");
                            return name.contains(simpleId);
                        })
                        .findFirst();
                        
                    if (orphanJar.isPresent()) {
                         toRemove.add(orphanJar.get());
                    }
                }
            }
        }
        
        // Execute removal
        for (Path p : toRemove) {
            Files.delete(p);
            success("Removed: " + p.getFileName());
        }
        
        if (toRemove.size() > 1) {
             System.out.println();
             info("Removed " + (toRemove.size() - 1) + " unused dependencies.");
        }
    }
    
    /**
     * Show project info
     */
    private void showProjectInfo() {
        System.out.println();
        System.out.println("  " + BOLD + WHITE + "Project Information" + RESET);
        System.out.println("  " + DIM + "─────────────────────────────────────────────────────────" + RESET);
        System.out.println();
        System.out.println("    " + CYAN + "⛏" + RESET + "  Minecraft Version:  " + BOLD + BRIGHT_GREEN + minecraftVersion + RESET);
        System.out.println("    " + MAGENTA + "⚡" + RESET + " Mod Loader:         " + BOLD + BRIGHT_YELLOW + capitalize(loader) + RESET);
        System.out.println("    " + BLUE + "📁" + RESET + " Project Root:       " + DIM + projectRoot + RESET);
        System.out.println("    " + GREEN + "📦" + RESET + " Mods Folder:        " + DIM + modsFolder + RESET);
        System.out.println();
        
        // Count installed mods
        try {
            if (Files.exists(modsFolder)) {
                long count = Files.list(modsFolder).filter(p -> p.toString().endsWith(".jar")).count();
                System.out.println("    " + BRIGHT_CYAN + "🔢" + RESET + " Installed Mods:     " + BOLD + count + RESET);
            }
        } catch (IOException ignored) {}
        System.out.println();
    }
    
    // === HTTP Helpers ===
    
    private JsonObject httpGetJson(String urlString) throws IOException {
        String response = httpGet(urlString);
        return JsonParser.parseString(response).getAsJsonObject();
    }
    
    private JsonArray httpGetJsonArray(String urlString) throws IOException {
        String response = httpGet(urlString);
        return JsonParser.parseString(response).getAsJsonArray();
    }
    
    private String httpGet(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "application/json");
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
    
    // === Formatting Helpers ===
    
    private String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }

    /**
     * Clean unused dependencies
     */
    private void cleanUnused() throws IOException {
        info("Analyzing installed mods for unused dependencies...");
        
        List<Path> allMods = Files.list(modsFolder)
            .filter(p -> p.toString().endsWith(".jar"))
            .toList();
            
        if (allMods.isEmpty()) {
            System.out.println("  No mods installed.");
            return;
        }
        
        // 1. Map each JAR to its ModID and its Dependencies
        Map<Path, String> jarToId = new HashMap<>();
        Map<Path, Set<String>> jarToDeps = new HashMap<>();
        
        for (Path jar : allMods) {
            String modId = getModIdFromJar(jar);
            if (modId != null) {
                jarToId.put(jar, modId);
            }
            jarToDeps.put(jar, getDependenciesFromJar(jar));
        }
        
        // 2. Identify usage count for each ModID
        Map<String, Integer> usageCount = new HashMap<>();
        for (Set<String> deps : jarToDeps.values()) {
            for (String dep : deps) {
                usageCount.put(dep, usageCount.getOrDefault(dep, 0) + 1);
            }
        }
        
        // 3. Find JARs that have ModID with usage == 0 (Roots)
        List<Path> candidates = new ArrayList<>();
        for (Path jar : allMods) {
            String id = jarToId.get(jar);
            // If we couldn't identify the ID, skip it (safe behavior)
            if (id == null) continue;
            
            // If no one depends on this ID
            if (usageCount.getOrDefault(id, 0) == 0) {
                // Heuristic: Is it likely a library?
                if (isLikelyLibrary(id, jar.getFileName().toString())) {
                    candidates.add(jar);
                }
            }
        }
        
        if (candidates.isEmpty()) {
            success("No unused libraries found.");
            return;
        }
        
        System.out.println();
        warning("Found " + candidates.size() + " potential orphan libraries:");
        for (Path p : candidates) {
            System.out.println("  " + DIM + "- " + RESET + p.getFileName());
        }
        System.out.println();
        
        info("Removing orphans...");
        for (Path p : candidates) {
            Files.delete(p);
            success("Removed: " + p.getFileName());
        }
    }
    
    private boolean isLikelyLibrary(String id, String filename) {
        String s = (id + filename).toLowerCase();
        return s.contains("lib") || s.contains("api") || s.contains("core") || 
               s.contains("config") || s.contains("cloth") || s.contains("balm") || 
               s.contains("bookshelf") || s.contains("architectury");
    }

    private String getModIdFromJar(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // 1. Fabric
            JarEntry fabricEntry = jar.getJarEntry("fabric.mod.json");
            if (fabricEntry != null) {
                try (InputStream is = jar.getInputStream(fabricEntry);
                     InputStreamReader reader = new InputStreamReader(is)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    return json.get("id").getAsString();
                }
            }
            // 2. NeoForge/Forge
            JarEntry forgeEntry = jar.getJarEntry("META-INF/mods.toml");
            if (forgeEntry == null) forgeEntry = jar.getJarEntry("META-INF/neoforge.mods.toml");
            
            if (forgeEntry != null) {
                try (InputStream is = jar.getInputStream(forgeEntry)) {
                    Scanner scanner = new Scanner(is);
                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine().trim();
                        if (line.startsWith("modId")) {
                            Matcher m = Pattern.compile("modId\\s*=\\s*\"([^\"]+)\"").matcher(line);
                            if (m.find()) return m.group(1);
                        }
                    }
                }
            }
        } catch (Exception e) {}
        return null; // Could not identify
    }

    private Set<String> getDependenciesFromJar(Path jarPath) {
        Set<String> deps = new HashSet<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            
            // 1. Try Fabric/Quilt (fabric.mod.json)
            JarEntry fabricEntry = jar.getJarEntry("fabric.mod.json");
            
            // 2. Try Forge/NeoForge (META-INF/mods.toml or neoforge.mods.toml)
            JarEntry forgeEntry = jar.getJarEntry("META-INF/mods.toml");
            if (forgeEntry == null) forgeEntry = jar.getJarEntry("META-INF/neoforge.mods.toml");
            
            if (fabricEntry != null) {
                try (InputStream is = jar.getInputStream(fabricEntry);
                     InputStreamReader reader = new InputStreamReader(is)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    if (json.has("depends")) {
                        JsonObject depends = json.getAsJsonObject("depends");
                        for (String depId : depends.keySet()) {
                            if (!isPlatformId(depId)) {
                                deps.add(depId);
                            }
                        }
                    }
                }
            } else if (forgeEntry != null) {
                 try (InputStream is = jar.getInputStream(forgeEntry)) {
                    // Simple TOML line scanner
                    Scanner scanner = new Scanner(is);
                    String pendingModId = null;
                    boolean pendingRequired = false;
                    
                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine().trim();
                        if (line.startsWith("#") || line.isEmpty()) continue;
                        
                        // Detect [[dependencies.modid]]
                        if (line.startsWith("[[dependencies")) {
                            if (pendingModId != null && pendingRequired && !isPlatformId(pendingModId)) {
                                deps.add(pendingModId);
                            }
                            pendingModId = null;
                            pendingRequired = false;
                        } else if (line.startsWith("modId")) {
                            Matcher m = Pattern.compile("modId\\s*=\\s*\"([^\"]+)\"").matcher(line);
                            if (m.find()) {
                                pendingModId = m.group(1);
                            }
                        } else if (line.startsWith("mandatory")) {
                            if (line.contains("true")) pendingRequired = true;
                        } else if (line.startsWith("type")) {
                            if (line.contains("required")) pendingRequired = true;
                        }
                    }
                    if (pendingModId != null && pendingRequired && !isPlatformId(pendingModId)) {
                        deps.add(pendingModId);
                    }
                 }
            }
            
        } catch (Exception e) {
            // Ignore errors silently in production
        }
        return deps;
    }
    
    private boolean isPlatformId(String id) {
        return id.equals("minecraft") || id.equals("java") || 
               id.equals("fabricloader") || id.equals("forge") || id.equals("neoforge");
    }
    
    private String formatDownloads(int downloads) {
        if (downloads >= 1_000_000) return String.format("%.1fM", downloads / 1_000_000.0);
        if (downloads >= 1_000) return String.format("%.1fK", downloads / 1_000.0);
        return String.valueOf(downloads);
    }
    
    private String formatSize(long bytes) {
        if (bytes >= 1_000_000) return String.format("%.1f MB", bytes / 1_000_000.0);
        if (bytes >= 1_000) return String.format("%.1f KB", bytes / 1_000.0);
        return bytes + " B";
    }
    
    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
    
    private String padRight(String s, int n) {
        if (n <= 0) return "";
        return String.format("%-" + n + "s", s);
    }
    
    private String shortenPath(String path, int max) {
        if (path.length() <= max) return path;
        return "..." + path.substring(path.length() - max + 3);
    }
    
    private static String repeat(String s, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(s);
        }
        return sb.toString();
    }
    /**
     * Format a row inside a box with proper dynamic padding.
     * The padding is calculated so the right border aligns perfectly regardless of content length.
     * 
     * Formula: paddingNeeded = boxWidth - visibleContentLength
     */
    private String formatBoxRow(String iconColor, String icon, String label, String value, int boxWidth) {
        // Strip ANSI codes to get visible length of value
        String visibleValue = value.replaceAll("\u001B\\[[;\\d]*m", "");
        
        // Build the visible content: "  icon label   value"
        // Icon = 1 char, space after icon = 1, label padded to 12 chars, value = variable
        int iconLen = 1;
        int labelWidth = 12;
        
        // Total visible content length (excluding borders and outer padding)
        // Structure: "  " + icon + " " + label(12) + value
        // = 2 + 1 + 1 + 12 + valueLen = 16 + valueLen
        int visibleContentLen = 2 + iconLen + 1 + labelWidth + visibleValue.length();
        
        // Calculate padding needed to reach boxWidth (interior width)
        int paddingNeeded = boxWidth - visibleContentLen;
        if (paddingNeeded < 0) paddingNeeded = 0;
        
        // Build the row
        StringBuilder row = new StringBuilder();
        row.append("  ");                                    // Left margin
        row.append(DIM).append(BOX_V).append(RESET);         // Left border │
        row.append("  ");                                    // Inner left padding
        row.append(iconColor).append(icon).append(RESET);    // Colored icon
        row.append(" ");                                     // Space after icon
        row.append(String.format("%-" + labelWidth + "s", label)); // Label padded to 12 chars
        row.append(value);                                   // Colored value
        row.append(repeat(" ", paddingNeeded));              // Dynamic padding
        row.append(DIM).append(BOX_V).append(RESET);         // Right border │
        
        return row.toString();
    }
}
