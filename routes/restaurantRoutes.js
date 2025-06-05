const express = require('express');
const router = express.Router();

module.exports = function(db) {
  // Get all restaurants with safety scores calculated based on user profile
  router.get('/restaurants', async (req, res) => {
  const { email } = req.query;
  
  if (!email) {
    return res.status(400).json({ success: false, message: "Email is required" });
  }
  
  try {
    // Get user's allergens
    const userAllergens = await new Promise((resolve, reject) => {
      db.all(
        "SELECT allergen, level FROM customer_allergens WHERE email = ?",
        [email],
        (err, rows) => {
          if (err) reject(err);
          else resolve(rows || []);
        }
      );
    });
    
    // Get user's dietary preferences
    const userDietaryPreferences = await new Promise((resolve, reject) => {
      db.all(
        "SELECT preference FROM customer_dietary_preferences WHERE email = ?",
        [email],
        (err, rows) => {
          if (err) reject(err);
          else resolve(rows ? rows.map(row => row.preference) : []);
        }
      );
    });
    
    // Get all restaurants - using a proper ORDER BY to ensure consistent results
    const restaurants = await new Promise((resolve, reject) => {
      db.all(
        "SELECT * FROM restaurants ORDER BY restaurant_name ASC",
        [],
        (err, rows) => {
          if (err) reject(err);
          else resolve(rows || []);
        }
      );
    });
    
    // For each restaurant, get menu items to calculate safety score
    const restaurantsWithScores = await Promise.all(restaurants.map(async (restaurant) => {
      // Get all menu items for the restaurant
      const menuItems = await new Promise((resolve, reject) => {
        db.all(
          "SELECT * FROM menu_items WHERE restaurant_id = ?",
          [restaurant.restaurant_id],
          (err, rows) => {
            if (err) reject(err);
            else resolve(rows || []);
          }
        );
      });
      
      // Get restaurant picture
      const restaurantPicture = await new Promise((resolve, reject) => {
        db.get(
          "SELECT picture_path FROM restaurant_pictures WHERE restaurant_id = ?",
          [restaurant.restaurant_id],
          (err, row) => {
            if (err) reject(err);
            else resolve(row);
          }
        );
      });
      
      // Create picture URL if picture exists
      const pictureUrl = restaurantPicture && restaurantPicture.picture_path 
        ? `/api/restaurant/${restaurant.restaurant_id}/picture?t=${Date.now()}` 
        : null;
      
      // If no menu items, default safety score
      if (menuItems.length === 0) {
        return {
          id: restaurant.restaurant_id.toString(), // Ensure ID is a string for mobile integration
          name: restaurant.restaurant_name,
          category: restaurant.restaurant_category || "General",
          allergenInfo: restaurant.restaurant_allergen_info || "No allergen information available",
          rating: restaurant.restaurant_rating || 0.0,
          safety_score: 80, // Base safety score with no dishes
          safetyScore: 80, // Added for consistency
          dietaryMatchScore: 0,
          imageUrl: pictureUrl
        };
      }
      
      // Calculate allergen points and dietary match points for each dish
      let dishScores = menuItems.map(item => {
        // Parse allergens and dietary preferences from JSON strings
        const itemAllergens = item.allergens ? JSON.parse(item.allergens) : [];
        const itemDietaryPreferences = item.dietary_preferences ? JSON.parse(item.dietary_preferences) : [];
        
        // Calculate allergen points for this item
        let allergenPoints = 0;
        for (const userAllergen of userAllergens) {
          if (itemAllergens.includes(userAllergen.allergen)) {
            // Assign points based on allergy level
            switch (userAllergen.level) {
              case 'severe':
                allergenPoints += 20;
                break;
              case 'moderate':
                allergenPoints += 10;
                break;
              case 'mild':
                allergenPoints += 5;
                break;
              default:
                allergenPoints += 1;
            }
          }
        }
        
        // Calculate dietary match points for this item (max 15 per dish)
        let dietaryMatchPoints = 0;
        for (const preference of userDietaryPreferences) {
          if (itemDietaryPreferences.includes(preference)) {
            dietaryMatchPoints += 3;
          }
        }
        dietaryMatchPoints = Math.min(15, dietaryMatchPoints);
        
        return {
          allergenPoints,
          dietaryMatchPoints
        };
      });
      
      // Calculate total allergen and dietary points across all dishes
      const totalAllergenPoints = dishScores.reduce((sum, dish) => sum + dish.allergenPoints, 0);
      const totalDietaryMatchPoints = dishScores.reduce((sum, dish) => sum + dish.dietaryMatchPoints, 0);
      
      // Calculate restaurant safety score as average of dish scores
      // For each dish: min(100, (80 - AllergenPoints + DietaryMatchPoints))
      const dishSafetyScores = dishScores.map(dish => 
        Math.min(100, (80 - dish.allergenPoints + dish.dietaryMatchPoints))
      );
      
      // Calculate average safety score across all dishes
      const avgSafetyScore = dishSafetyScores.reduce((sum, score) => sum + score, 0) / dishSafetyScores.length;
      
      // Ensure score is between 0-100
      const finalSafetyScore = Math.max(0, Math.min(100, Math.round(avgSafetyScore)));
      
      // Calculate dietary match percentage
      const maxPossibleDietaryPoints = userDietaryPreferences.length > 0 ? 
        Math.min(15, userDietaryPreferences.length * 3) * menuItems.length : 0;
      const dietaryMatchScore = maxPossibleDietaryPoints > 0 ? 
        Math.min(100, Math.round((totalDietaryMatchPoints / maxPossibleDietaryPoints) * 100)) : 0;
      
      return {
        id: restaurant.restaurant_id.toString(), // Ensure ID is a string for mobile integration
        name: restaurant.restaurant_name,
        category: restaurant.restaurant_category || "General",
        allergenInfo: restaurant.restaurant_allergen_info || "No allergen information available",
        rating: restaurant.restaurant_rating || 0.0,
        safety_score: finalSafetyScore, // Kept for backward compatibility
        safetyScore: finalSafetyScore, // Property name matching the mobile model
        dietaryMatchScore: dietaryMatchScore,
        imageUrl: pictureUrl
      };
    }));
    
    return res.status(200).json({
      success: true,
      restaurants: restaurantsWithScores
    });
  } catch (error) {
    console.error("Error getting restaurants:", error);
    return res.status(500).json({ success: false, message: "Database error" });
  }
}); 
  
  // Get safety score count (number of safe restaurants for user)
  router.get('/customer/safety-score', async (req, res) => {
    const { email } = req.query;
    
    if (!email) {
      return res.status(400).json({ success: false, message: "Email is required" });
    }
    
    try {
      // Get user's allergens
      const userAllergens = await new Promise((resolve, reject) => {
        db.all(
          "SELECT allergen, level FROM customer_allergens WHERE email = ?",
          [email],
          (err, rows) => {
            if (err) reject(err);
            else resolve(rows || []);
          }
        );
      });
      
      // Get user's dietary preferences
      const userDietaryPreferences = await new Promise((resolve, reject) => {
        db.all(
          "SELECT preference FROM customer_dietary_preferences WHERE email = ?",
          [email],
          (err, rows) => {
            if (err) reject(err);
            else resolve(rows ? rows.map(row => row.preference) : []);
          }
        );
      });
      
      // Count restaurants with safety score above threshold (70%)
      const safetyThreshold = 70;
      
      // Get all restaurants
      const restaurants = await new Promise((resolve, reject) => {
        db.all(
          "SELECT * FROM restaurants",
          [],
          (err, rows) => {
            if (err) reject(err);
            else resolve(rows || []);
          }
        );
      });
      
      // Calculate safe restaurant count
      let safeCount = 0;
      
      for (const restaurant of restaurants) {
        // Get all menu items for the restaurant
        const menuItems = await new Promise((resolve, reject) => {
          db.all(
            "SELECT * FROM menu_items WHERE restaurant_id = ?",
            [restaurant.restaurant_id],
            (err, rows) => {
              if (err) reject(err);
              else resolve(rows || []);
            }
          );
        });
        
        // If no menu items, use default safety score
        if (menuItems.length === 0) {
          if (80 >= safetyThreshold) {
            safeCount++;
          }
          continue;
        }
        
        // Calculate allergen points and dietary match points for each dish
        let dishScores = menuItems.map(item => {
          const itemAllergens = item.allergens ? JSON.parse(item.allergens) : [];
          const itemDietary = item.dietary_preferences ? JSON.parse(item.dietary_preferences) : [];
          
          // Calculate allergen points for this item
          let allergenPoints = 0;
          for (const userAllergen of userAllergens) {
            if (itemAllergens.includes(userAllergen.allergen)) {
              switch (userAllergen.level) {
                case 'severe': allergenPoints += 20; break;
                case 'moderate': allergenPoints += 10; break;
                case 'mild': allergenPoints += 5; break;
                default: allergenPoints += 1;
              }
            }
          }
          
          // Calculate dietary match points for this item (max 15 per dish)
          let dietaryMatchPoints = 0;
          for (const pref of userDietaryPreferences) {
            if (itemDietary.includes(pref)) {
              dietaryMatchPoints += 3;
            }
          }
          dietaryMatchPoints = Math.min(15, dietaryMatchPoints);
          
          return {
            allergenPoints,
            dietaryMatchPoints
          };
        });
        
        // NEW FORMULA: Calculate dish safety scores
        const dishSafetyScores = dishScores.map(dish => 
          Math.min(100, (80 - dish.allergenPoints + dish.dietaryMatchPoints))
        );
        
        // Calculate average safety score across all dishes
        const avgSafetyScore = dishSafetyScores.reduce((sum, score) => sum + score, 0) / dishSafetyScores.length;
        
        // Ensure score is between 0-100
        const finalSafetyScore = Math.max(0, Math.min(100, Math.round(avgSafetyScore)));
        
        // Increment counter if restaurant is considered safe
        if (finalSafetyScore >= safetyThreshold) {
          safeCount++;
        }
      }
      
      return res.status(200).json({
        success: true,
        safeCount: safeCount
      });
    } catch (error) {
      console.error("Error calculating safe restaurant count:", error);
      return res.status(500).json({ success: false, message: "Database error" });
    }
  });
  
  // Get menu items for a specific restaurant with safety scores based on user profile
  router.get('/menu', async (req, res) => {
    const { restaurant_id, email } = req.query;

    if (!restaurant_id) {
      return res.status(400).json({ success: false, message: "Restaurant ID is required" });
    }

    try {
      // Fetch user prefs only if email is provided
      let userAllergens = [];
      let userDietaryPreferences = [];

      if (email) {
        userAllergens = await new Promise((resolve, reject) => {
          db.all(
            "SELECT allergen, level FROM customer_allergens WHERE email = ?",
            [email],
            (err, rows) => err ? reject(err) : resolve(rows || [])
          );
        });

        userDietaryPreferences = await new Promise((resolve, reject) => {
          db.all(
            "SELECT preference FROM customer_dietary_preferences WHERE email = ?",
            [email],
            (err, rows) => err
              ? reject(err)
              : resolve(rows ? rows.map(r => r.preference) : [])
          );
        });
      }

      // Pull all menu items for this restaurant - ORDER BY ensures consistent display
      const menuItems = await new Promise((resolve, reject) => {
        db.all(
          "SELECT * FROM menu_items WHERE restaurant_id = ? ORDER BY dish_type, dish_name",
          [restaurant_id],
          (err, rows) => err ? reject(err) : resolve(rows || [])
        );
      });

      // Compute per‐dish safety and return
      const menuItemsWithScores = menuItems.map(item => {
        // parse JSON fields
        const allergensList = item.allergens ? JSON.parse(item.allergens) : [];
        const dietaryList = item.dietary_preferences
                           ? JSON.parse(item.dietary_preferences)
                           : [];

        // allergen penalty
        let allergenPoints = 0;
        for (const ua of userAllergens) {
          if (allergensList.includes(ua.allergen)) {
            switch (ua.level) {
              case 'severe':   allergenPoints += 20; break;
              case 'moderate': allergenPoints += 10; break;
              case 'mild':     allergenPoints += 5;  break;
              default:         allergenPoints += 1;
            }
          }
        }

        // dietary bonus (max 15)
        let dietaryPoints = 0;
        for (const pref of userDietaryPreferences) {
          if (dietaryList.includes(pref)) dietaryPoints += 3;
        }
        dietaryPoints = Math.min(15, dietaryPoints);

        // new formula
        const rawScore = 80 - allergenPoints + dietaryPoints;
        const bounded = Math.min(100, Math.max(0, rawScore));
        const itemSafety = Math.round(bounded);

        return {
          item_id: item.item_id.toString(), // Ensure ID is a string for better mobile compatibility
          id: item.item_id.toString(), // Additional field for mobile model compatibility
          name: item.dish_name,
          description: item.dish_description || "No description available",
          category: item.dish_type,
          ingredients: item.dish_ingredients || "Ingredients information not available",
          allergens: item.allergens,
          dietary_preferences: item.dietary_preferences,
          // Both formats for compatibility
          safety_score: itemSafety,
          safetyScore: itemSafety,
          // Added image URL using the standard endpoint pattern
          image_url: `/api/menuitem/${item.item_id}/picture?t=${Date.now()}`,
          imageUrl: `/api/menuitem/${item.item_id}/picture?t=${Date.now()}`
        };
      });

      return res.status(200).json({
        success: true,
        menu_items: menuItemsWithScores
      });
    } catch (error) {
      console.error("Error getting menu items:", error);
      return res.status(500).json({ success: false, message: "Database error" });
    }
  });
  
  router.get('/dish-analysis', async (req, res) => {
    const { item_id, email } = req.query;
    
    if (!item_id) {
      return res.status(400).json({ success: false, message: "Item ID is required" });
    }
    
    if (!email) {
      return res.status(400).json({ success: false, message: "Email is required" });
    }
    
    try {
      // Get item details
      const menuItem = await new Promise((resolve, reject) => {
        db.get(
          "SELECT * FROM menu_items WHERE item_id = ?",
          [item_id],
          (err, row) => {
            if (err) reject(err);
            else if (!row) reject(new Error("Menu item not found"));
            else resolve(row);
          }
        );
      });
      
      // Parse JSON fields
      const dishAllergens = menuItem.allergens ? JSON.parse(menuItem.allergens) : [];
      const dishDietaryPreferences = menuItem.dietary_preferences ? JSON.parse(menuItem.dietary_preferences) : [];
      
      // Get user's allergens
      const userAllergens = await new Promise((resolve, reject) => {
        db.all(
          "SELECT allergen, level FROM customer_allergens WHERE email = ?",
          [email],
          (err, rows) => {
            if (err) reject(err);
            else resolve(rows || []);
          }
        );
      });
      
      // Get user's dietary preferences
      const userDietaryPreferences = await new Promise((resolve, reject) => {
        db.all(
          "SELECT preference FROM customer_dietary_preferences WHERE email = ?",
          [email],
          (err, rows) => {
            if (err) reject(err);
            else resolve(rows ? rows.map(row => row.preference) : []);
          }
        );
      });
      
      // Calculate allergen points for this item
      let allergenPoints = 0;
      let hasSevereAllergen = false;
      let allergenCheck = [];
      
      // First, check user's known allergens
      userAllergens.forEach(userAllergen => {
        const isSafe = !dishAllergens.includes(userAllergen.allergen);
        
        // Add to allergen check list
        allergenCheck.push({
          allergen: userAllergen.allergen,
          is_safe: isSafe,
          severity: userAllergen.level
        });
        
        // Calculate points if not safe
        if (!isSafe) {
          switch (userAllergen.level) {
            case 'severe':
              allergenPoints += 20;
              hasSevereAllergen = true;
              break;
            case 'moderate':
              allergenPoints += 10;
              break;
            case 'mild':
              allergenPoints += 5;
              break;
            default:
              allergenPoints += 1;
          }
        }
      });
      
      // Add common allergens not in user's list but present in the dish
      dishAllergens.forEach(allergen => {
        const alreadyChecked = allergenCheck.some(item => item.allergen === allergen);
        if (!alreadyChecked) {
          allergenCheck.push({
            allergen: allergen,
            is_safe: true, // Safe because user didn't declare allergy to it
            severity: null
          });
        }
      });
      
      // Calculate dietary match points for this item (max 15)
      let dietaryMatchPoints = 0;
      let dietaryCompatibility = [];
      
      // Check each user preference against dish
      userDietaryPreferences.forEach(preference => {
        const isCompatible = dishDietaryPreferences.includes(preference);
        
        // Add to dietary compatibility list
        dietaryCompatibility.push({
          preference: preference,
          is_compatible: isCompatible
        });
        
        // Calculate points if compatible
        if (isCompatible) {
          dietaryMatchPoints += 3;
        }
      });
      
      // Cap dietary points at 15
      dietaryMatchPoints = Math.min(15, dietaryMatchPoints);
      
      // Calculate safety score using the formula
      let safetyScore = 80 - allergenPoints + dietaryMatchPoints;
      
      // Ensure score is between 0-100
      safetyScore = Math.max(0, Math.min(100, Math.round(safetyScore)));
      
      // Generate analysis summary based on findings
      let analysisSummary = "This dish ";
      if (hasSevereAllergen) {
        analysisSummary += "contains a severe dairy allergy, recommend not safe to eat.";
      } else if (allergenPoints > 0) {
        analysisSummary += "contains some allergens that you've identified, but they are of lower severity.";
      } else if (dietaryMatchPoints === 0 && userDietaryPreferences.length > 0) {
        analysisSummary += "doesn't match any of your dietary preferences.";
      } else if (dietaryMatchPoints > 0) {
        analysisSummary += "is compatible with some of your dietary preferences.";
      } else {
        analysisSummary += "appears to be safe based on your profile.";
      }
      
      // Generate overall recommendation
      let overallRecommendation = "";
      if (safetyScore >= 85) {
        overallRecommendation = "This dish is likely safe for you to consume based on your dietary profile.";
      } else if (safetyScore >= 70) {
        overallRecommendation = "This dish has moderate compatibility with your dietary needs. Exercise caution.";
      } else {
        overallRecommendation = "This dish contains a severe dairy allergy, recommend not safe to eat.";
      }
      
      // Complete analysis object
      const analysis = {
        safety_score: safetyScore,
        dietary_compatibility: dietaryCompatibility,
        allergen_check: allergenCheck,
        analysis_summary: analysisSummary,
        overall_recommendation: overallRecommendation
      };
      
      return res.status(200).json({
        success: true,
        analysis: analysis
      });
    } catch (error) {
      console.error("Error analyzing dish:", error);
      return res.status(500).json({ success: false, message: error.message || "Database error" });
    }
  });
  
  
  return router;
};