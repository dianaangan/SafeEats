const express = require("express");
const sqlite3 = require("sqlite3").verbose();
const fs = require("fs");
const path = require("path");
const multer = require('multer');
const crypto = require("crypto");
const nodemailer = require("nodemailer");
const bcrypt = require("bcrypt");
const { OAuth2Client } = require("google-auth-library");
const cors = require("cors");
require("dotenv").config();

// Initialize express app
const app = express();
const PORT = process.env.PORT || 3000;
const client = new OAuth2Client(process.env.GOOGLE_CLIENT_ID);

// Configure middleware
app.use(cors());
app.use(express.urlencoded({ extended: true }));
app.use(express.json());
app.use(express.static(path.join(__dirname, "public")));

// Configure file uploads
const uploadDir = path.join(__dirname, 'uploads');
if (!fs.existsSync(uploadDir)) {
  fs.mkdirSync(uploadDir);
}

const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, uploadDir),
  filename: (req, file, cb) => cb(null, req.body.email + path.extname(file.originalname))
});

const upload = multer({
  storage: storage,
  limits: { fileSize: 2 * 1024 * 1024 }, // 2MB limit
  fileFilter: (req, file, cb) => {
    file.mimetype.startsWith('image/') ? cb(null, true) : cb(new Error('Only images are allowed'));
  }
});

// Setup email
const transporter = nodemailer.createTransport({
  service: 'gmail',
  auth: {
    user: 'safeeats89@gmail.com',
    pass: 'nzdl wgvt gaoz xhdf'
  }
});

// Verify email connection
transporter.verify((error, success) => {
  error ? console.log("Email server error:", error) : console.log("Email server is ready");
});

// Initialize database
const db = new sqlite3.Database(path.join(__dirname, "safeeats.db"), (err) => {
  if (err) {
    console.error("Error opening database:", err.message);
    return;
  }
  console.log("Database opened successfully");
  
  // Create tables in series
  db.serialize(() => {
    // Users table
    db.run(`CREATE TABLE IF NOT EXISTS customers (
      email TEXT PRIMARY KEY,
      firstname TEXT,
      middlename TEXT,
      lastname TEXT,
      password TEXT,
      registrationType TEXT
    );`);
    
    // Admin table
    db.run(`CREATE TABLE IF NOT EXISTS admins (
      email TEXT PRIMARY KEY,
      password TEXT,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );`);
    
    // Restaurant table
    db.run(`CREATE TABLE IF NOT EXISTS restaurants (
      restaurant_id INTEGER PRIMARY KEY,
      restaurant_name TEXT NOT NULL,
      restaurant_category TEXT NOT NULL,
      restaurant_description TEXT,
      restaurant_allergen_info TEXT,
      restaurant_rating REAL
    );`);
    
    // Create the restaurant pictures table
    db.run(`CREATE TABLE IF NOT EXISTS restaurant_pictures (
      restaurant_id INTEGER PRIMARY KEY,
      picture_path TEXT,
      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (restaurant_id) REFERENCES restaurants(restaurant_id)
    );`);
    
    // Menu items table with JSON arrays for dietary_preferences and allergens
    db.run(`CREATE TABLE IF NOT EXISTS menu_items (
      item_id INTEGER PRIMARY KEY AUTOINCREMENT,
      restaurant_id INTEGER NOT NULL,
      dish_name TEXT NOT NULL,
      dish_type TEXT NOT NULL,
      dish_description TEXT,
      dish_ingredients TEXT,
      dietary_preferences TEXT, /* Stored as JSON array: ["Vegan", "Gluten-Free"] */
      allergens TEXT, /* Stored as JSON array: ["Dairy", "Nuts"] */
      FOREIGN KEY (restaurant_id) REFERENCES restaurants(restaurant_id)
    );`);
    
    // Create the menu item pictures table
    db.run(`CREATE TABLE IF NOT EXISTS menu_item_pictures (
      item_id INTEGER PRIMARY KEY,
      picture_path TEXT,
      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (item_id) REFERENCES menu_items(item_id)
    );`);
    
    // Analyzed items table
    db.run(`CREATE TABLE IF NOT EXISTS analyzed_items (
      analysis_id INTEGER PRIMARY KEY AUTOINCREMENT,
      item_id INTEGER NOT NULL,
      email TEXT NOT NULL,
      dish_name TEXT NOT NULL,
      dish_description TEXT,
      safety_score INTEGER NOT NULL,
      analysis_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      dietary_compatibility TEXT,
      allergen_check TEXT,
      analysis_summary TEXT,
      overall_recommendation TEXT,
      FOREIGN KEY (item_id) REFERENCES menu_items(item_id)
    );`);
    
    //Notification Table
    db.run(`CREATE TABLE IF NOT EXISTS notifications (
      notification_id INTEGER PRIMARY KEY AUTOINCREMENT,
      email TEXT NOT NULL,
      item_id INTEGER NOT NULL,
      dish_safety_score INTEGER NOT NULL,
      dish_name TEXT NOT NULL,
      dish_description TEXT,
      analyzedTime TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (email) REFERENCES customers(email),
      FOREIGN KEY (item_id) REFERENCES menu_items(item_id)
    );`);
    
    //notification seen status for count display
    db.run(`CREATE TABLE IF NOT EXISTS notification_seen_status (
            email TEXT PRIMARY KEY,
            last_seen_count INTEGER DEFAULT 0,
            last_seen_time DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (email) REFERENCES customers(email)
        );`);

    // Reset codes table
    db.run(`CREATE TABLE IF NOT EXISTS reset_codes (
      email TEXT PRIMARY KEY,
      code TEXT,
      expires_at INTEGER,
      attempts INTEGER DEFAULT 0
    );`);
    
    // Reset tokens table
    db.run(`CREATE TABLE IF NOT EXISTS reset_tokens (
      email TEXT PRIMARY KEY,
      token TEXT,
      expires_at INTEGER
    );`);
  
    // Customer Dietary Preferences table
    db.run(`CREATE TABLE IF NOT EXISTS customer_dietary_preferences (
      email TEXT,
      preference TEXT,
      PRIMARY KEY (email, preference),
      FOREIGN KEY (email) REFERENCES customers(email)
    );`);
    
    // Customer Allergens table
    db.run(`CREATE TABLE IF NOT EXISTS customer_allergens (
      email TEXT,
      allergen TEXT,
      level TEXT,
      PRIMARY KEY (email, allergen),
      FOREIGN KEY (email) REFERENCES customers(email)
    );`);
    
    // Customer Pictures table
    db.run(`CREATE TABLE IF NOT EXISTS customer_pictures (
      email TEXT PRIMARY KEY,
      picture_path TEXT,
      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (email) REFERENCES customers(email)
    );`);
    
    // Initialize admin account
    const adminEmail = "admin@safeeats.com";
    const adminPassword = "safeeatsadmin";
    
    // Check if admin already exists
    db.get("SELECT email FROM admins WHERE email = ?", [adminEmail], (err, row) => {
      if (err) {
        console.error("Error checking for admin:", err);
        return;
      }
      
      if (!row) {
        // Admin doesn't exist, create one
        bcrypt.hash(adminPassword, 10, (hashErr, hash) => {
          if (hashErr) {
            console.error("Error hashing admin password:", hashErr);
            return;
          }
          
          db.run("INSERT INTO admins (email, password) VALUES (?, ?)", 
            [adminEmail, hash], 
            (insertErr) => {
              if (insertErr) {
                console.error("Error creating admin:", insertErr);
              } else {
                console.log("Admin account created successfully");
              }
            }
          );
        });
      }
    });
  });
});

// Helper Functions
const sendEmail = async (to, subject, text, html) => {
  try {
    const mailOptions = {
      from: '"SafeEats Support" <safeeats89@gmail.com>',
      to,
      subject,
      text,
      html
    };

    const info = await transporter.sendMail(mailOptions);
    console.log("Email sent successfully:", info.messageId);
    return true;
  } catch (error) {
    console.error("Error sending email:", error);
    return false;
  }
};

const getUser = (email) => {
  return new Promise((resolve, reject) => {
    db.get("SELECT * FROM customers WHERE email = ?", [email], (err, user) => {
      if (err) reject(err);
      resolve(user);
    });
  });
};

const getAdmin = (email) => {
  return new Promise((resolve, reject) => {
    db.get("SELECT * FROM admins WHERE email = ?", [email], (err, admin) => {
      if (err) reject(err);
      resolve(admin);
    });
  });
};

const generateResetCode = async (email) => {
  const code = Math.floor(1000 + Math.random() * 9000).toString();
  const expiresAt = Date.now() + 15 * 60 * 1000; // 15 minutes
  
  await new Promise((resolve, reject) => {
    db.run(
      "REPLACE INTO reset_codes (email, code, expires_at, attempts) VALUES (?, ?, ?, 0)",
      [email, code, expiresAt],
      (err) => err ? reject(err) : resolve()
    );
  });
  
  return code;
};

// Admin Login Route
app.post("/api/admin/login", async (req, res) => {
  const { email, password } = req.body;
  
  if (!email || !password) {
    return res.status(400).json({ success: false, message: "Email and password are required" });
  }
  
  try {
    const admin = await getAdmin(email);
    
    if (!admin) {
      return res.status(401).json({ success: false, message: "Invalid admin credentials" });
    }
    
    const isPasswordValid = await bcrypt.compare(password, admin.password);
    
    if (!isPasswordValid) {
      return res.status(401).json({ success: false, message: "Invalid admin credentials" });
    }
    
    return res.status(200).json({
      success: true,
      message: "Admin login successful",
      userType: "admin",
      email: admin.email
    });
  } catch (error) {
    console.error("Admin login error:", error);
    return res.status(500).json({ success: false, message: "Error processing admin login" });
  }
});

// Google Sign in Route
app.post("/api/customer/google-login", async (req, res) => {
  const { idToken } = req.body;
  
  if (!idToken) {
    return res.status(400).json({ success: false, message: "No ID token provided" });
  }

  try {
    console.log("Starting Google token verification. Client ID:", process.env.GOOGLE_CLIENT_ID);
    
    // Verify the Google Token
    const ticket = await client.verifyIdToken({
      idToken,
      audience: process.env.GOOGLE_CLIENT_ID,
    });

    const payload = ticket.getPayload();
    const { email, given_name, family_name } = payload;
    
    console.log("Google token verified successfully for:", email);
    
    if (!email) {
      return res.status(400).json({ success: false, message: "Email not provided by Google" });
    }

    // Check if user exists
    const user = await getUser(email);
    
    if (user) {
      // User already exists, log them in
      return res.status(200).json({
        success: true,
        message: "Login successful",
        userType: "customer",
        email: user.email,
        firstname: user.firstname,
        lastname: user.lastname,
        isNewAccount: false
      });
    } else {
      // Register new Google user
      return new Promise((resolve, reject) => {
        db.run(
          "INSERT INTO customers (email, firstname, lastname, password, registrationType) VALUES (?, ?, ?, ?, ?)",
          [email, given_name, family_name, null, "google"],
          function(err) {
            if (err) {
              reject(err);
              return;
            }
            
            resolve(res.status(201).json({
              success: true,
              message: "User registered successfully",
              userType: "customer",
              email,
              firstname: given_name,
              lastname: family_name,
              isNewAccount: true
            }));
          }
        );
      });
    }
  } catch (error) {
    // More detailed error logging
    console.error("Google token verification error:", error);
    console.error("Token verification failed with details:", {
      errorName: error.name,
      errorMessage: error.message,
      clientId: process.env.GOOGLE_CLIENT_ID
    });
    
    return res.status(400).json({ 
      success: false, 
      message: "Google authentication failed. Please try a different login method.",
      error: error.message
    });
  }
});

// Login Route (Modified to handle both customer and admin)
app.post("/api/customer/login", async (req, res) => {
  const { email, password } = req.body;
  
  if (!email || !password) {
    return res.status(400).json({ success: false, message: "Email and password are required" });
  }
  
  try {
    // Check if it's an admin first
    if (email === "admin@safeeats.com") {
      const admin = await getAdmin(email);
      
      if (admin) {
        const isPasswordValid = await bcrypt.compare(password, admin.password);
        
        if (isPasswordValid) {
          return res.status(200).json({
            success: true,
            message: "Admin login successful",
            userType: "admin",
            email: admin.email
          });
        }
      }
      
      return res.status(401).json({ success: false, message: "Invalid admin credentials" });
    }
    
    // Handle regular customer login
    const user = await getUser(email);
    
    if (!user) {
      return res.status(401).json({ success: false, message: "Invalid email or password" });
    }
    
    const userType = user.registrationType || 'email';
    
    if (userType === 'email') {
      const isPasswordValid = await bcrypt.compare(password, user.password);
      
      if (!isPasswordValid) {
        return res.status(401).json({ success: false, message: "Invalid email or password" });
      }
      
      return res.status(200).json({
        success: true,
        message: "Login successful",
        userType: "customer",
        email: user.email,
        firstname: user.firstname,
        lastname: user.lastname
      });
    } 
    else if (userType === 'google') {
      return res.status(400).json({ 
        success: false, 
        message: "This account uses Google Sign-In. Please login with Google." 
      });
    }
    else {
      return res.status(400).json({ success: false, message: "Invalid login method for this account" });
    }
  } catch (error) {
    console.error("Login error:", error);
    return res.status(500).json({ success: false, message: "Error processing login" });
  }
});

// Register Route
app.post("/api/customer/register", async (req, res) => {
  const { email, firstname, middlename, lastname, password } = req.body;
  
  if (!email || !password || !firstname || !lastname) {
    return res.status(400).json({ 
      success: false, 
      message: "Email, first name, last name, and password are required"
    });
  }
  
  // Prevent registration with admin email
  if (email === "admin@safeeats.com") {
    return res.status(400).json({ 
      success: false, 
      message: "Cannot register with admin email address"
    });
  }
  
  try {
    // Validate email format
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      return res.status(400).json({ success: false, message: "Invalid email format" });
    }

    // Validate password strength
    if (password.length < 6) {
      return res.status(400).json({ success: false, message: "Password must be at least 6 characters long" });
    }
    
    // Check if user exists
    const existingUser = await getUser(email);
    if (existingUser) {
      return res.status(409).json({ success: false, message: "Email already registered" });
    }
    
    // Hash the password
    const hashedPassword = await bcrypt.hash(password, 10);
    
    // Insert customer into database
    await new Promise((resolve, reject) => {
      db.run(
        "INSERT INTO customers (email, firstname, middlename, lastname, password, registrationType) VALUES (?, ?, ?, ?, ?, ?)",
        [email, firstname, middlename || null, lastname, hashedPassword, 'email'],
        function (err) {
          if (err) reject(err);
          else resolve();
        }
      );
    });
    
    return res.status(201).json({ success: true, message: "User registered successfully" });
  } catch (error) {
    console.error("Registration error:", error);
    return res.status(500).json({ success: false, message: "Error processing registration" });
  }
});

// Check setup status
app.get("/api/customer/setup-status", async (req, res) => {
  const { email } = req.query;
  
  if (!email) {
    return res.status(400).json({ success: false, message: "Email is required" });
  }
  
  try {
    const result = await new Promise((resolve, reject) => {
      db.get(
        `SELECT COUNT(*) as count FROM 
         (SELECT email FROM customer_dietary_preferences WHERE email = ? 
          UNION 
          SELECT email FROM customer_allergens WHERE email = ?) AS setup`,
        [email, email],
        (err, row) => {
          if (err) reject(err);
          else resolve(row);
        }
      );
    });
    
    return res.status(200).json({
      success: true,
      hasCompletedSetup: result.count > 0
    });
  } catch (error) {
    console.error("Setup status error:", error);
    return res.status(500).json({ success: false, message: "Database error" });
  }
});

// Admin verification middleware
const verifyAdmin = async (req, res, next) => {
  const { email } = req.body;
  
  if (email !== "admin@safeeats.com") {
    return res.status(403).json({ success: false, message: "Admin access required" });
  }
  
  const admin = await getAdmin(email);
  if (!admin) {
    return res.status(403).json({ success: false, message: "Admin access required" });
  }
  
  req.isAdmin = true;
  next();
};

// Admin dashboard stats endpoint
app.get("/api/admin/stats", async (req, res) => {
  const { email } = req.query;
  
  if (email !== "admin@safeeats.com") {
    return res.status(403).json({ success: false, message: "Admin access required" });
  }
  
  try {
    const stats = {};
    
    // Get total customers
    stats.totalCustomers = await new Promise((resolve, reject) => {
      db.get("SELECT COUNT(*) as count FROM customers", (err, row) => {
        if (err) reject(err);
        else resolve(row.count);
      });
    });
    
    // Get total restaurants
    stats.totalRestaurants = await new Promise((resolve, reject) => {
      db.get("SELECT COUNT(*) as count FROM restaurants", (err, row) => {
        if (err) reject(err);
        else resolve(row.count);
      });
    });
    
    // Get total menu items
    stats.totalMenuItems = await new Promise((resolve, reject) => {
      db.get("SELECT COUNT(*) as count FROM menu_items", (err, row) => {
        if (err) reject(err);
        else resolve(row.count);
      });
    });
    
    // Get total analyses
    stats.totalAnalyses = await new Promise((resolve, reject) => {
      db.get("SELECT COUNT(*) as count FROM analyzed_items", (err, row) => {
        if (err) reject(err);
        else resolve(row.count);
      });
    });
    
    return res.status(200).json({
      success: true,
      stats
    });
  } catch (error) {
    console.error("Admin stats error:", error);
    return res.status(500).json({ success: false, message: "Error fetching admin stats" });
  }
});

// Restaurant & Menu Items Picture routes
const restaurantPictureRoutes = require('./restaurantPictureRoutes');
const menuItemPictureRoutes = require('./menuItemPictureRoutes');
app.use('/api', restaurantPictureRoutes(db));
app.use('/api', menuItemPictureRoutes(db));

// Import and use the notification
const notificationRoutes = require('./notificationRoutes');
app.use('/api', notificationRoutes(db));

// Import and use the restaurant routes - this is the main route for displaying restaurants in the home activity
const restaurantRoutes = require('./restaurantRoutes');
app.use('/api', restaurantRoutes(db));

// Import the password reset routes
const passwordResetRoutes = require('./passwordResetRoutes');
app.use('/api', passwordResetRoutes(db, sendEmail, getUser, generateResetCode));

// Import the dietary preference routes
const dietaryPreferenceRoutes = require('./dietaryPreferenceRoutes')(db);
app.use('/api/customer', dietaryPreferenceRoutes);

// Import the allergen routes
const allergenRoutes = require('./allergenRoutes')(db);
app.use('/api/customer', allergenRoutes);

// Import the user profile routes
const profileRoutes = require('./profileRoutes');
app.use('/api/customer', profileRoutes(db, upload, getUser));

// Utility endpoint for mobile app health check
app.get('/api/test', (req, res) => {
  res.json({ success: true, message: 'Server is running', status: 'ok' });
});

// Endpoint to provide Google client ID to frontend
app.get('/api/getClientId', (req, res) => {
  console.log("Client ID request received, serving GOOGLE_CLIENT_ID:", process.env.GOOGLE_CLIENT_ID);
  
  if (process.env.GOOGLE_CLIENT_ID) {
    // Include allowed origins to help with client-side setup
    const allowedOrigins = [
      'https://swamp-brief-brake.glitch.me',
      'https://safeeats.glitch.me', // Add any other domains your app might use
      'http://localhost:3000',      // For local development
      'http://localhost:5000'       // For local development
    ];
    
    res.json({ 
      success: true, 
      clientId: process.env.GOOGLE_CLIENT_ID,
      allowedOrigins: allowedOrigins,
      serverTime: new Date().toISOString()
    });
  } else {
    console.error("GOOGLE_CLIENT_ID is not configured in environment variables");
    res.status(500).json({ 
      success: false, 
      message: 'Google client ID not configured on server',
      serverTime: new Date().toISOString()
    });
  }
});

// Start Server
app.listen(PORT, () => {
  console.log(`SafeEats API is running on port ${PORT}`);
});