package org.frizzlenpop.frizzlenMod.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.frizzlenpop.frizzlenMod.FrizzlenMod;
import org.frizzlenpop.frizzlenMod.api.controllers.AppealsController;
import org.frizzlenpop.frizzlenMod.api.controllers.AuthController;
import org.frizzlenpop.frizzlenMod.api.controllers.DashboardController;
import org.frizzlenpop.frizzlenMod.api.controllers.ModLogsController;
import org.frizzlenpop.frizzlenMod.api.controllers.PunishmentsController;
import org.frizzlenpop.frizzlenMod.api.controllers.UsersController;
import org.frizzlenpop.frizzlenMod.api.middleware.AuthMiddleware;
import org.frizzlenpop.frizzlenMod.api.middleware.CorsMiddleware;
import spark.Spark;

import java.io.File;
import java.net.URL;
import java.util.logging.Level;

import static spark.Spark.*;

public class WebApiManager {
    private final FrizzlenMod plugin;
    private final Gson gson;
    private final int port;
    private final String jwtSecret;
    
    private AppealsController appealsController;
    private AuthController authController;
    private DashboardController dashboardController;
    private ModLogsController modLogsController;
    private PunishmentsController punishmentsController;
    private UsersController usersController;
    
    public WebApiManager(FrizzlenMod plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        
        // Load config
        this.port = plugin.getConfig().getInt("web-api.port", 8080);
        this.jwtSecret = plugin.getConfig().getString("web-api.jwt-secret", "change-me-in-config");
        
        // Check if JWT secret is default
        if (this.jwtSecret.equals("change-me-in-config")) {
            plugin.getLogger().warning("Using default JWT secret! Please change this in the config.yml for security.");
        }
        
        // Initialize controllers
        initializeControllers();
    }
    
    private void initializeControllers() {
        this.appealsController = new AppealsController(plugin, gson);
        this.authController = new AuthController(plugin, gson, jwtSecret);
        this.dashboardController = new DashboardController(plugin, gson);
        this.modLogsController = new ModLogsController(plugin, gson);
        this.punishmentsController = new PunishmentsController(plugin, gson);
        this.usersController = new UsersController(plugin, gson);
    }
    
    /**
     * Starts the web server and sets up all routes
     */
    public void start() {
        try {
            // Configure Spark
            port(this.port);
            
            // Serve static files from web directory
            String webPath = plugin.getDataFolder() + File.separator + "web";
            staticFiles.externalLocation(webPath);
            
            // Initialize static web files if they don't exist
            initializeWebDirectory(webPath);
            
            // Set up CORS
            before(new CorsMiddleware());
            
            // Add explicit route for root path to serve index.html
            get("/", (req, res) -> {
                File indexFile = new File(webPath, "index.html");
                if (indexFile.exists()) {
                    res.type("text/html");
                    return new String(java.nio.file.Files.readAllBytes(indexFile.toPath()));
                } else {
                    res.status(404);
                    return "Index file not found";
                }
            });
            
            // Add explicit route for wrapper.html
            get("/wrapper.html", (req, res) -> {
                File wrapperFile = new File(webPath, "wrapper.html");
                if (wrapperFile.exists()) {
                    res.type("text/html");
                    return new String(java.nio.file.Files.readAllBytes(wrapperFile.toPath()));
                } else {
                    res.status(404);
                    return "Wrapper file not found";
                }
            });
            
            // Public routes
            path("/api", () -> {
                // Auth routes
                post("/auth/login", authController::login);
                
                // Appeal submission (public)
                post("/appeals/submit", appealsController::submitAppeal);
                get("/appeals/status/:uuid", appealsController::getAppealStatus);
                
                // Protected routes
                path("/admin", () -> {
                    before("/*", new AuthMiddleware(jwtSecret));
                    
                    // Dashboard routes
                    get("/dashboard/stats", dashboardController::getStats);
                    get("/dashboard/recent-logs", dashboardController::getRecentLogs);
                    get("/dashboard/recent-appeals", dashboardController::getRecentAppeals);
                    
                    // Punishment management
                    get("/punishments", punishmentsController::getAllPunishments);
                    get("/punishments/player/:player", punishmentsController::getPlayerPunishments);
                    post("/punishments/ban", punishmentsController::addBan);
                    post("/punishments/unban/:player", punishmentsController::removeBan);
                    
                    // Add warn endpoint
                    post("/punishments/warn/:player", punishmentsController::warnPlayer);
                    
                    // Add history endpoint
                    get("/punishments/history/:player", punishmentsController::getPlayerHistory);
                    
                    // Appeals management
                    get("/appeals", appealsController::getAllAppeals);
                    get("/appeals/:id", appealsController::getAppeal);
                    post("/appeals/:id/approve", appealsController::approveAppeal);
                    post("/appeals/:id/deny", appealsController::denyAppeal);
                    post("/appeals/:id/comment", appealsController::addComment);
                    
                    // Moderation logs
                    get("/logs", modLogsController::getAllLogs);
                    get("/logs/:player", modLogsController::getPlayerLogs);
                    
                    // Add modlogs endpoints (aligned with what frontend expects)
                    get("/modlogs", modLogsController::getAllLogs);
                    get("/modlogs/player/:player", modLogsController::getPlayerLogs);
                    get("/modlogs/action/:action", modLogsController::getLogsByAction);
                    get("/modlogs/timerange", modLogsController::getLogsByTimeRange);
                    
                    // User management endpoints
                    get("/users", usersController::getAllUsers);
                    post("/users", usersController::createUser);
                    get("/users/:username", usersController::getUser);
                    put("/users/:username/password", usersController::updatePassword);
                    put("/users/:username/role", usersController::updateRole);
                    delete("/users/:username", usersController::deleteUser);
                });
            });
            
            // Error handling
            exception(Exception.class, (e, req, res) -> {
                plugin.getLogger().log(Level.SEVERE, "API Error: " + e.getMessage(), e);
                res.status(500);
                res.type("application/json");
                res.body(gson.toJson(new ErrorResponse("Internal server error")));
            });
            
            // Log startup
            plugin.getLogger().info("Web API started on port " + this.port);
            plugin.getLogger().info("Web Panel URL: http://localhost:" + this.port);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start web API: " + e.getMessage(), e);
        }
    }
    
    /**
     * Stops the web server
     */
    public void stop() {
        Spark.stop();
        plugin.getLogger().info("Web API stopped");
    }
    
    /**
     * Initialize the web directory with default files if they don't exist
     */
    private void initializeWebDirectory(String webPath) {
        try {
            File webDir = new File(webPath);
            if (!webDir.exists()) {
                webDir.mkdirs();
                
                // Copy web resources from plugin jar
                copyWebResources(webDir);
                
                plugin.getLogger().info("Created web panel directory at " + webPath);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize web directory: " + e.getMessage(), e);
        }
    }
    
    /**
     * Copy web resources from the plugin jar to the web directory
     */
    private void copyWebResources(File webDir) {
        try {
            // Copy main index.html
            plugin.saveResource("web/index.html", false);
            
            // Copy JS files
            copyResourceDirectory("web/js", webDir);
            
            // Copy CSS files
            copyResourceDirectory("web/css", webDir);
            
            // Copy image files
            copyResourceDirectory("web/img", webDir);
            
            // Create admin panel directory and copy files
            File adminDir = new File(webDir, "admin");
            if (!adminDir.exists()) {
                adminDir.mkdirs();
            }
            
            // Check if admin index exists in jar
            try {
                plugin.saveResource("web/admin/index.html", false);
            } catch (Exception e) {
                // May not exist in the jar, create a simple redirect
                createSimpleAdminRedirect(adminDir);
            }
            

            
            plugin.getLogger().info("Web resources copied successfully");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not copy all web resources: " + e.getMessage(), e);
            plugin.getLogger().warning("The web panel may not function correctly. Please check your installation.");
        }
    }
    
    /**
     * Copy a directory of resources from the plugin jar
     */
    private void copyResourceDirectory(String resourcePath, File targetDir) {
        try {
            // This is a workaround since we can't list resources in a jar
            // We'll copy known resource files
        	
        	if (resourcePath.equals("web")) {
        		plugin.saveResource("modlogs.yml", false);
        	}
        	
            if (resourcePath.equals("web/js")) {
                plugin.saveResource("web/js/main.js", false);
                plugin.saveResource("web/js/modlogs.js", false);
                plugin.saveResource("web/js/users.js", false);
                plugin.saveResource("web/js/auth.js", false);
                plugin.saveResource("web/js/api.js", false);
                plugin.saveResource("web/js/dashboard.js", false);
                plugin.saveResource("web/js/punishments.js", false);
                plugin.saveResource("web/js/appeals.js", false);
            } else if (resourcePath.equals("web/css")) {
                plugin.saveResource("web/css/style.css", false);
                plugin.saveResource("web/css/login.css", false);
                plugin.saveResource("web/css/dashboard.css", false);
            } else if (resourcePath.equals("web/img")) {
                plugin.saveResource("web/img/logo.png", false);
                plugin.saveResource("web/img/background.jpg", false);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error copying directory " + resourcePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Create a simple redirect page for admin
     */
    private void createSimpleAdminRedirect(File adminDir) {
        try {
            File indexFile = new File(adminDir, "index.html");
            if (!indexFile.exists()) {
                java.io.FileWriter writer = new java.io.FileWriter(indexFile);
                writer.write("<!DOCTYPE html>\n<html><head><meta http-equiv=\"refresh\" content=\"0; url=../index.html\"></head></html>");
                writer.close();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not create admin redirect: " + e.getMessage());
        }
    }
    
    /**
     * Error response class for API errors
     */
    private static class ErrorResponse {
        private final String error;
        
        public ErrorResponse(String error) {
            this.error = error;
        }
    }
} 