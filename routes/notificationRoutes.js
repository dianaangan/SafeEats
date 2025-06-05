const express = require('express');

module.exports = function(db) {
  const router = express.Router();

  // Store a new notification
  router.post('/notifications', async (req, res) => {
    const { email, item_id, dish_safety_score, dish_name, dish_description } = req.body;
    
    // Validate required fields
    if (!email || !item_id || dish_safety_score === undefined || !dish_name) {
      return res.status(400).json({ 
        success: false, 
        message: 'Missing required fields. Email, item_id, dish_safety_score, and dish_name are required.'
      });
    }
    
    try {
      // Create new notification
      db.run(
        `INSERT INTO notifications 
        (email, item_id, dish_safety_score, dish_name, dish_description)
        VALUES (?, ?, ?, ?, ?)`,
        [email, item_id, dish_safety_score, dish_name, dish_description || null],
        function(err) {
          if (err) {
            console.error('Error in storing notification:', err);
            return res.status(500).json({
              success: false,
              message: 'Error storing notification in database'
            });
          }

          res.status(201).json({
            success: true,
            message: 'Notification stored successfully'
          });
        }
      );
    } catch (error) {
      console.error('Error in storing notification:', error);
      res.status(500).json({
        success: false,
        message: 'Error storing notification in database'
      });
    }
  });

  // Get all notifications for a user
  router.get('/notifications', async (req, res) => {
    const { email } = req.query;
    
    if (!email) {
      return res.status(400).json({
        success: false,
        message: 'Email is required'
      });
    }

    try {
      db.all(
        `SELECT * FROM notifications 
         WHERE email = ? 
         ORDER BY analyzedTime DESC`,
        [email],
        (err, notifications) => {
          if (err) {
            console.error('Error fetching notifications:', err);
            return res.status(500).json({
              success: false,
              message: 'Error fetching notifications from database'
            });
          }
          
          res.status(200).json({
            success: true,
            notifications
          });
        }
      );
    } catch (error) {
      console.error('Error fetching notifications:', error);
      res.status(500).json({
        success: false,
        message: 'Error fetching notifications from database'
      });
    }
  });

  // Delete all notifications for a user
  router.delete('/notifications/clear-all', async (req, res) => {
    const { email } = req.query;

    if (!email) {
      return res.status(400).json({
        success: false,
        message: 'Email is required'
      });
    }

    try {
      // First verify if the user has any notifications
      db.get(
        "SELECT COUNT(*) as count FROM notifications WHERE email = ?", 
        [email],
        (err, row) => {
          if (err) {
            console.error('Error counting notifications:', err);
            return res.status(500).json({
              success: false,
              message: 'Error accessing database'
            });
          }

          if (row.count === 0) {
            return res.status(404).json({
              success: false,
              message: 'No notifications found for this user'
            });
          }

          // Delete all notifications for the user
          db.run(
            "DELETE FROM notifications WHERE email = ?",
            [email],
            function(err) {
              if (err) {
                console.error('Error deleting notifications:', err);
                return res.status(500).json({
                  success: false,
                  message: 'Error deleting notifications from database'
                });
              }
              
              const deletedCount = this.changes;
              
              // After successful deletion, update the notification seen count
              db.run(
                `INSERT OR REPLACE INTO notification_seen_status
                 (email, last_seen_count, last_seen_time)
                 VALUES (?, 0, CURRENT_TIMESTAMP)`,
                [email],
                (err) => {
                  if (err) {
                    console.error('Error updating notification status:', err);
                    // Continue even if this fails
                  }
                  
                  res.status(200).json({
                    success: true,
                    message: `All ${deletedCount} notifications deleted successfully`
                  });
                }
              );
            }
          );
        }
      );
    } catch (error) {
      console.error('Error deleting notifications:', error);
      res.status(500).json({
        success: false,
        message: 'Error deleting notifications from database'
      });
    }
  });

  // Simple endpoint to clear all notifications (backup solution)
  router.delete('/notifications/erase-all', async (req, res) => {
    const { email } = req.query;

    if (!email) {
      return res.status(400).json({
        success: false,
        message: 'Email is required'
      });
    }

    console.log(`Attempting to erase all notifications for email: ${email}`);

    try {
      // Simple direct delete
      db.run(
        "DELETE FROM notifications WHERE email = ?",
        [email],
        function(err) {
          if (err) {
            console.error('Error in erase-all operation:', err);
            return res.status(500).json({
              success: false,
              message: 'Error erasing notifications from database'
            });
          }
          
          const deletedCount = this.changes;
          
          // Update seen count
          db.run(
            `INSERT OR REPLACE INTO notification_seen_status
             (email, last_seen_count, last_seen_time)
             VALUES (?, 0, CURRENT_TIMESTAMP)`,
            [email],
            (err) => {
              if (err) {
                console.error('Error updating notification status:', err);
                // Continue even if this fails
              }
              
              // Return success
              res.status(200).json({
                success: true,
                message: `Successfully erased ${deletedCount} notifications`,
                count: deletedCount
              });
            }
          );
        }
      );
    } catch (error) {
      console.error('Error in erase-all operation:', error);
      res.status(500).json({
        success: false,
        message: 'Error erasing notifications from database'
      });
    }
  });

  // Mark notifications as seen
  router.put('/notifications/seen', async (req, res) => {
    const { email } = req.query;
    
    if (!email) {
      return res.status(400).json({
        success: false,
        message: 'Email is required'
      });
    }

    try {
      // Get current notification count
      db.get(
        "SELECT COUNT(*) as count FROM notifications WHERE email = ?",
        [email],
        (err, row) => {
          if (err) {
            console.error('Error counting notifications:', err);
            return res.status(500).json({
              success: false,
              message: 'Error accessing database'
            });
          }
          
          const count = row.count;
          
          // Update or insert seen status
          db.run(
            `INSERT OR REPLACE INTO notification_seen_status
             (email, last_seen_count, last_seen_time)
             VALUES (?, ?, CURRENT_TIMESTAMP)`,
            [email, count],
            (err) => {
              if (err) {
                console.error('Error marking notifications as seen:', err);
                return res.status(500).json({
                  success: false,
                  message: 'Error updating notification seen status'
                });
              }
              
              res.status(200).json({
                success: true,
                message: 'Notifications marked as seen'
              });
            }
          );
        }
      );
    } catch (error) {
      console.error('Error marking notifications as seen:', error);
      res.status(500).json({
        success: false,
        message: 'Error updating notification seen status'
      });
    }
  });

  // Get notification count
  router.get('/notifications/count', async (req, res) => {
    const { email } = req.query;
    
    if (!email) {
      return res.status(400).json({
        success: false,
        message: 'Email is required'
      });
    }

    try {
      // Get total notifications count
      db.get(
        "SELECT COUNT(*) as count FROM notifications WHERE email = ?",
        [email],
        (err, totalRow) => {
          if (err) {
            console.error('Error counting notifications:', err);
            return res.status(500).json({
              success: false,
              message: 'Error accessing database'
            });
          }
          
          const totalCount = totalRow.count;
          
          // Get last seen count
          db.get(
            "SELECT last_seen_count FROM notification_seen_status WHERE email = ?",
            [email],
            (err, statusRow) => {
              if (err) {
                console.error('Error getting notification status:', err);
                return res.status(500).json({
                  success: false,
                  message: 'Error accessing database'
                });
              }
              
              const lastSeenCount = statusRow ? statusRow.last_seen_count : 0;
              
              // Calculate new notifications (total - last seen)
              const newCount = Math.max(0, totalCount - lastSeenCount);
              
              res.status(200).json({
                success: true,
                count: newCount
              });
            }
          );
        }
      );
    } catch (error) {
      console.error('Error counting notifications:', error);
      res.status(500).json({
        success: false,
        message: 'Error counting notifications in database'
      });
    }
  });

  return router;
}; 