package com.example.livo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.util.Log;

//import com.example.livo.company.order.OrderModelClass;
import com.example.livo.customer.CustomerSession;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class Database extends SQLiteOpenHelper {

    public static final String DATABASE_NAME= "LIVO.db";
    private static final String TAG = "Database";
    private FirebaseFirestore firestore;

    public Database(Context context) {
        super(context, DATABASE_NAME, null, 1);
        firestore = FirebaseFirestore.getInstance();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Enable foreign key support
        db.execSQL("PRAGMA foreign_keys=ON;");

        // Create userLogin_data table
        db.execSQL("CREATE TABLE userLogin_data (" +
                "user_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "email TEXT, " +
                "password TEXT)");

        // Create userData table with foreign key reference to userLogin_data(email)
        String createTableQuery = "CREATE TABLE userData (" +
                "user_id INTEGER, " +
                "name TEXT, " +
                "address TEXT, " +
                "profile_pic BLOB, " +
                "contact TEXT, " +
                "CONSTRAINT fk_user_id FOREIGN KEY (user_id) REFERENCES userLogin_data(user_id) ON DELETE CASCADE" +
                ")";
        db.execSQL(createTableQuery);

        // Create orders table with foreign key reference to userLogin_data(user_id)
        String createTableQuery2 = "CREATE TABLE orders (" +
                "order_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER, " +
                "total_price DOUBLE, " +
                "order_status TEXT, " +
                "company_email TEXT," +
                "created_at TEXT, " +
                "CONSTRAINT fk_user_id FOREIGN KEY (user_id) REFERENCES userLogin_data(user_id) ON DELETE CASCADE" +
                ")";
        db.execSQL(createTableQuery2);

        // Create order_items table with foreign key reference to orders(order_id)
        String createTableQuery3 = "CREATE TABLE order_items (" +
                "order_item_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "order_id INTEGER, " +
                "product_id INTEGER, " +
                "product_name TEXT, " +
                "quantity INTEGER, " +
                "price DOUBLE, " +
                "created_at TEXT, " +
                "CONSTRAINT fk_order_id FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE" +
                ")";
        db.execSQL(createTableQuery3);

        String createTableQuery4 = "CREATE TABLE products (" +
                "product_id INTEGER PRIMARY KEY, " +
                "companyID TEXT, " +
                "product_name TEXT, " +
                "description TEXT, " +
                "quantity INTEGER, " +
                "price DOUBLE, " +
                "imageUrl TEXT, " +
                "modelUrl TEXT, " +
                "CONSTRAINT fk_companyID FOREIGN KEY (companyID) REFERENCES companies(companyID) ON DELETE CASCADE" +
                ")";
        db.execSQL(createTableQuery4);

    }

    public void fetchProductsFromFirebaseAndInsert(final String companyID) {
        firestore.collection("companyData")
                .document(companyID)
                .collection("products")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        SQLiteDatabase db = this.getWritableDatabase(); // Open the database once
                        db.beginTransaction(); // Begin a transaction for batch inserts
                        try {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                // Extract product fields from Firebase
                                String productId = document.getId();
                                String productName = document.getString("name");
                                double price = document.contains("price") ? document.getDouble("price") : 0.0;
                                String description = document.getString("description");
                                int quantity = document.contains("quantity") ? document.getLong("quantity").intValue() : 0;
                                String imageUrl = document.getString("imageUrl");
                                String modelUrl = document.getString("modelUrl");

                                // Insert product into SQLite
                                insertProductIntoSQLite(db, productId, productName, price, description, quantity, imageUrl, modelUrl);
                            }
                            db.setTransactionSuccessful(); // Mark transaction as successful
                        } catch (Exception e) {
                            Log.e("SQLiteError", "Error inserting products: ", e);
                        } finally {
                            db.endTransaction(); // End the transaction
                            db.close(); // Close the database
                        }
                    } else {
                        Log.d(TAG, "Error getting products: ", task.getException());
                    }
                });
    }

    private void insertProductIntoSQLite(SQLiteDatabase db, String productId, String productName, double price, String description, int quantity, String imageUrl, String modelUrl) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("product_id", productId);
        contentValues.put("product_name", productName);
        contentValues.put("price", price);
        contentValues.put("description", description);
        contentValues.put("quantity", quantity);
        contentValues.put("image_url", imageUrl);
        contentValues.put("model_url", modelUrl);

        // Insert the product data into the products table
        long result = db.insertWithOnConflict("products", null, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
        if (result == -1) {
            Log.d("SQLiteError", "Failed to insert product: " + productId);
        } else {
            Log.d("SQLiteSuccess", "Product inserted successfully: " + productId);
        }
    }


    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {

    }

    //userLogin_data TABLE OPERATIONS
    public boolean insertData(String email, String password){
        SQLiteDatabase db= this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("email", email);
        contentValues.put("password", password);
        long result = db.insert("userLogin_data", null, contentValues);
        return result != -1;
    }

    public boolean checkEmail(String email) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM userLogin_data WHERE email = ?", new String[]{email});
        return cursor.getCount() > 0;
    }

    public boolean checkEmailPassword(String email, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM userLogin_data WHERE email = ? AND password = ?", new String[]{email, password});
        return cursor.getCount() > 0;
    }

    public boolean insertUserdata(String name, String address, Bitmap image, String contact, Context context) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = null;
        try {
            // Retrieve email from CustomerSession
            CustomerSession customerSession = CustomerSession.getInstance(context);
            String email = customerSession.getEmail();

            if (email == null || email.isEmpty()) {
                Log.e(TAG, "No email found in session");
                return false; // Email is required for the foreign key check
            }

            // Fetch user_id for the given email
            cursor = db.rawQuery("SELECT user_id FROM userLogin_data WHERE email = ?", new String[]{email});
            if (cursor.moveToFirst()) {
                int userId = cursor.getInt(0); // Get the user_id

                // Compress the Bitmap image
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                image.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                byte[] imageInBytes = outputStream.toByteArray();

                // Prepare values for insertion
                ContentValues values = new ContentValues();
                values.put("user_id", userId);
                values.put("name", name);
                values.put("address", address);
                values.put("profile_pic", imageInBytes);
                values.put("contact", contact);

                // Insert data into userData table
                long result = db.insert("userData", null, values);
                if (result != -1) {
                    Log.d(TAG, "Data inserted successfully");
                    return true;
                } else {
                    Log.e(TAG, "Error inserting user data");
                    return false;
                }
            } else {
                Log.e(TAG, "Email does not exist in userLogin_data table");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error inserting user data: " + e.getMessage());
            return false;
        } finally {
            if (cursor != null) {
                cursor.close(); // Close cursor to avoid memory leaks
            }
        }
    }

    public boolean updateUserData(int userId, Bitmap profileImg, String cusName, String newEmail, String address, String contact) {
        SQLiteDatabase db = this.getWritableDatabase();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        db.beginTransaction(); // Begin a transaction to ensure atomicity
        try {
            // Update userData table
            ContentValues userDataValues = new ContentValues();

            // Convert Bitmap to byte array if provided
            if (profileImg != null) {
                profileImg.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                byte[] imageInBytes = outputStream.toByteArray();
                userDataValues.put("profile_pic", imageInBytes);
            }

            // Add values to update in userData
            if (cusName != null && !cusName.isEmpty()) {
                userDataValues.put("name", cusName);
            }
            if (address != null && !address.isEmpty()) {
                userDataValues.put("address", address);
            }
            if (contact != null && !contact.isEmpty()) {
                userDataValues.put("contact", contact);
            }

            // Update the userData table
            int userDataRowsUpdated = db.update("userData", userDataValues, "user_id = ?", new String[]{String.valueOf(userId)});

            // Update email in userLogin_data table
            if (newEmail != null && !newEmail.isEmpty()) {
                ContentValues userLoginValues = new ContentValues();
                userLoginValues.put("email", newEmail);

                int userLoginRowsUpdated = db.update("userLogin_data", userLoginValues, "user_id = ?", new String[]{String.valueOf(userId)});

                // Check if userLogin_data update failed
                if (userLoginRowsUpdated <= 0) {
                    throw new Exception("Failed to update email in userLogin_data.");
                }
            }

            // Commit the transaction if all updates succeed
            db.setTransactionSuccessful();
            return userDataRowsUpdated > 0;

        } catch (Exception e) {
            Log.e("Database", "Error updating user data: " + e.getMessage());
            return false;
        } finally {
            try {
                outputStream.close();
            } catch (IOException e) {
                Log.e("Database", "Error closing stream: " + e.getMessage());
            }
            db.endTransaction(); // End the transaction
        }
    }

    public Cursor getUserData(int userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery(
                "SELECT u.name, u.address, u.profile_pic, u.contact, l.email " +
                        "FROM userData u INNER JOIN userLogin_data l ON u.user_id = l.user_id " +
                        "WHERE u.user_id = ?",
                new String[]{String.valueOf(userId)}
        );
    }

    //ORDER TABLE OPERATIONS
    // Insert into orders table
    public long insertOrder(int userId, double totalPrice, String orderStatus, String companyEmail, String createdAt) {
        SQLiteDatabase db = this.getWritableDatabase();
        long orderId = -1;
        try {
            ContentValues values = new ContentValues();
            values.put("user_id", userId);
            values.put("total_price", totalPrice);
            values.put("order_status", orderStatus);
            values.put("company_email", companyEmail);
            values.put("created_at", createdAt);

            orderId = db.insert("orders", null, values);
            if (orderId != -1) {
                Log.d(TAG, "Order inserted successfully with ID: " + orderId);
            } else {
                Log.e(TAG, "Error inserting order");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error inserting order: " + e.getMessage());
        }
        return orderId; // Return the order ID for foreign key usage
    }

    // Insert into order_items table
    public boolean insertOrderItem(long orderId, int productId, String productName, int quantity, double price, String createdAt) {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put("order_id", orderId);
            values.put("product_id", productId);
            values.put("product_name", productName);
            values.put("quantity", quantity);
            values.put("price", price);
            values.put("created_at", createdAt);

            long id = db.insert("order_items", null, values);
            if (id != -1) {
                Log.d(TAG, "Order item inserted successfully with ID: " + id);
                return true;
            } else {
                Log.e(TAG, "Error inserting order item");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error inserting order item: " + e.getMessage());
            return false;
        }
    }

    public boolean saveOrder(int userId, double totalAmount, String orderStatus, String companyEmail, String createdAt) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("user_id", userId);
        values.put("total_amount", totalAmount);
        values.put("status", orderStatus);
        values.put("company_email", companyEmail);
        values.put("created_at", createdAt);

        long orderId = db.insert("Orders", null, values);
        return orderId != -1; // Returns true if the order was saved successfully
    }

    public boolean saveOrderItem(String productId, int quantity, double price, double totalPrice, String createdAt) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("product_id", productId);
        values.put("quantity", quantity);
        values.put("price", price);
        values.put("total_price", totalPrice);
        values.put("created_at", createdAt);

        long result = db.insert("OrderItems", null, values);
        return result != -1; // Returns true if the order item was saved successfully
    }



     Comapny side db
    public List<OrderModelClass> getOrdersByCompanyEmail(String companyEmail) {
        SQLiteDatabase db = this.getReadableDatabase();
        List<OrderModelClass> orderList = new ArrayList<>();

        String query = "SELECT * FROM orders WHERE company_email = ?";
        Cursor cursor = db.rawQuery(query, new String[]{companyEmail});

        if (cursor.moveToFirst()) {
            do {
                @SuppressLint("Range") int orderID = cursor.getInt(cursor.getColumnIndex("order_id"));
                @SuppressLint("Range") String orderDate = cursor.getString(cursor.getColumnIndex("order_date"));
                @SuppressLint("Range")String productID = cursor.getString(cursor.getColumnIndex("product_id"));
                @SuppressLint("Range")int quantity = cursor.getInt(cursor.getColumnIndex("quantity"));
                @SuppressLint("Range")double price = cursor.getDouble(cursor.getColumnIndex("price"));
                @SuppressLint("Range")String orderStatus = cursor.getString(cursor.getColumnIndex("order_status"));

                orderList.add(new OrderModelClass(orderID, orderDate, productID, quantity, price, orderStatus));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return orderList;
    }


}
