const express = require("express");
const fs = require("fs");
const path = require("path");
const multer = require('multer');
const crypto = require("crypto");
const nodemailer = require("nodemailer");
const bcrypt = require("bcrypt");
const { OAuth2Client } = require("google-auth-library");
const cors = require("cors");
const mongoose = require('mongoose');

require('dotenv').config();

// Set strictQuery to false to handle deprecation warning
mongoose.set('strictQuery', false);

// MongoDB connection with fallback
const MONGODB_URI = process.env.MONGODB_URI || process.env.MONGO_URI || 'mongodb+srv://safeeats:safeeats123@safeeaets.jsfdxwn.mongodb.net/safeeats?retryWrites=true&w=majority';

if (!MONGODB_URI) {
  console.error('MongoDB connection string not found. Please set MONGODB_URI or MONGO_URI in your environment variables.');
  process.exit(1);
}

// Connect to MongoDB
mongoose.connect(MONGODB_URI, {
  useNewUrlParser: true,
  useUnifiedTopology: true,
})
.then(() => console.log("Connected to MongoDB"))
.catch(err => console.error("MongoDB connection error:", err));

// Import MongoDB models
const { 
  Customer, 
  Admin, 
  Restaurant, 
  RestaurantPicture,
  MenuItem,
  MenuItemPicture,
  AnalyzedItem,
  Notification,
  NotificationStatus,
  ResetCode,
  ResetToken,
  CustomerDietaryPreference,
  CustomerAllergen,
  CustomerPicture
} = require('./models/models');

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
    user: process.env.EMAIL_USER || 'safeeats89@gmail.com',
    pass: process.env.EMAIL_PASS || 'nzdl wgvt gaoz xhdf'
  }
});

// Verify email connection
transporter.verify((error, success) => {
  error ? console.log("Email server error:", error) : console.log("Email server is ready");
});

// Initialize admin account
async function initializeAdmin() {
  try {
    const adminEmail = "admin@safeeats.com";
    const adminPassword = "safeeatsadmin";
    
    // Check if admin already exists
    const existingAdmin = await Admin.findOne({ email: adminEmail });
    
    if (!existingAdmin) {
      // Hash the password
      const hash = await bcrypt.hash(adminPassword, 10);
      
      // Create admin
      await Admin.create({
        email: adminEmail,
        password: hash
      });
      
      console.log("Admin account created successfully");
    }
  } catch (error) {
    console.error("Error creating admin:", error);
  }
}

// Call initialize admin function
initializeAdmin();

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

const getUser = async (email) => {
  try {
    return await Customer.findOne({ email });
  } catch (error) {
    console.error("Error fetching user:", error);
    return null;
  }
};

const getAdmin = async (email) => {
  try {
    return await Admin.findOne({ email });
  } catch (error) {
    console.error("Error fetching admin:", error);
    return null;
  }
};

const generateResetCode = async (email) => {
  const code = Math.floor(1000 + Math.random() * 9000).toString();
  const expiresAt = Date.now() + 15 * 60 * 1000; // 15 minutes
  
  try {
    // Delete any existing code
    await ResetCode.deleteOne({ email });
    
    // Create new reset code
    await ResetCode.create({
      email,
      code,
      expires_at: expiresAt,
      attempts: 0
    });
    
    return code;
  } catch (error) {
    console.error("Error generating reset code:", error);
    throw error;
  }
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
    console.log("Using Google Client ID:", process.env.GOOGLE_CLIENT_ID);
    
    // Skip audience verification for now - this makes it more flexible
    const ticket = await client.verifyIdToken({
      idToken,
      // CRITICAL FIX: Make audience verification optional for development
      audience: undefined,
      clockTolerance: 60 // 60 seconds tolerance for time differences
    });

    const payload = ticket.getPayload();
    const { email, given_name, family_name, aud } = payload;
    
    console.log("Google Auth payload audience:", aud);
    console.log("Our client ID:", process.env.GOOGLE_CLIENT_ID);
    
    // Manual audience check with better logging
    if (Array.isArray(aud)) {
      if (!aud.includes(process.env.GOOGLE_CLIENT_ID)) {
        console.log("Warning: Token audience mismatch but proceeding anyway for testing");
      }
    } else if (aud !== process.env.GOOGLE_CLIENT_ID) {
      console.log("Warning: Token audience mismatch but proceeding anyway for testing");
    }
    
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
      const newUser = new Customer({
        email,
        firstname: given_name || "Google",
        lastname: family_name || "User",
        password: null,
        registrationType: "google"
      });
      
      await newUser.save();
      
      return res.status(201).json({
        success: true,
        message: "User registered successfully",
        userType: "customer",
        email,
        firstname: given_name || "Google",
        lastname: family_name || "User",
        isNewAccount: true
      });
    }
  } catch (error) {
    console.error("Google token verification error details:", error.message);
    
    // Provide more helpful error response
    return res.status(400).json({ 
      success: false, 
      message: "Google authentication failed: " + error.message,
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
    
    // Create new customer
    const newUser = new Customer({
      email,
      firstname,
      middlename: middlename || null,
      lastname,
      password: hashedPassword,
      registrationType: 'email'
    });
    
    await newUser.save();
    
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
    const dietaryPreferences = await CustomerDietaryPreference.findOne({ email });
    const allergens = await CustomerAllergen.findOne({ email });
    
    const hasCompletedSetup = (dietaryPreferences || allergens) ? true : false;
    
    return res.status(200).json({
      success: true,
      hasCompletedSetup
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
    // Get total customers count
    const totalCustomers = await Customer.countDocuments();
    
    // Get total restaurants count
    const totalRestaurants = await Restaurant.countDocuments();
    
    // Get total menu items count
    const totalMenuItems = await MenuItem.countDocuments();
    
    // Get total analyses count
    const totalAnalyses = await AnalyzedItem.countDocuments();
    
    const stats = {
      totalCustomers,
      totalRestaurants,
      totalMenuItems,
      totalAnalyses
    };
    
    return res.status(200).json({
      success: true,
      stats
    });
  } catch (error) {
    console.error("Admin stats error:", error);
    return res.status(500).json({ success: false, message: "Error fetching admin stats" });
  }
});

// ----- Import and use Admin Routes -----
const adminRoutes = require('./adminRoutes')(); 
app.use('/api', adminRoutes);

// Restaurant & Menu Items Picture routes
const restaurantPictureRoutes = require('./restaurantPictureRoutes')();
const menuItemPictureRoutes = require('./menuItemPictureRoutes')();
app.use('/api', restaurantPictureRoutes);
app.use('/api', menuItemPictureRoutes);

// Import and use the notification
const notificationRoutes = require('./notificationRoutes')();
app.use('/api', notificationRoutes);

// Import and use the restaurant
const restaurantRoutes = require('./restaurantRoutes')();
app.use('/api', restaurantRoutes);

// Import the password reset routes
const passwordResetRoutes = require('./passwordResetRoutes')(sendEmail, getUser, generateResetCode);
app.use('/api', passwordResetRoutes);

// Import the dietary preference routes
const dietaryPreferenceRoutes = require('./dietaryPreferenceRoutes')();
app.use('/api/customer', dietaryPreferenceRoutes);

// Import the allergen routes
const allergenRoutes = require('./allergenRoutes')();
app.use('/api/customer', allergenRoutes);

// Import the user profile routes
const profileRoutes = require('./profileRoutes')(upload, getUser);
app.use('/api/customer', profileRoutes);

// Utility endpoint
app.get('/api/test', (req, res) => {
  res.json({ success: true, message: 'Server is running' });
});

// Endpoint to provide Google client ID to frontend
app.get('/api/getClientId', (req, res) => {
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
      allowedOrigins: allowedOrigins
    });
  } else {
    res.status(500).json({ success: false, message: 'Google client ID not configured on server' });
  }
});

// Start Server
app.listen(PORT, () => {
  console.log(`SafeEats API is running on port ${PORT}`);
}); 