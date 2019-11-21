package com.akp.ceg4110.quickreports;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class DatabaseAccessor
{

    public static final String DATABASE_NAME = "Incidents";

    public static final String INCIDENT_TABLE = "incident_table";
    public static final String NAME_COLUMN = "name";
    public static final String DESCRIPTION_COLUMN = "description";
    public static final String INCIDENT_WEATHER = "weather";

    public static final String PICTURE_TABLE  = "image_table";
    public static final String PICTURE_NAME_COLUMN = "name";
    public static final String PICTURE_PRIMARY_COLUMN = "picture_reference";
    public static final String PICTURE_COLUMN = "picture";

    private SQLiteDatabase db;

    /**
     * DatabaseAccessor constructor. Creates the tables incident_table and image_table for the database
     * @param db SQLiteDatabase that will be the database for this accessor class. Example creation:
     *           DatabaseAccessor db = new DatabaseAccessor(this.openOrCreateDatabase(DatabaseAccessor.DATABASE_NAME, MODE_PRIVATE, null));
     */
    public DatabaseAccessor(SQLiteDatabase db){
        this.db = db;
        //Build string for creating the incident_table table
        //TEMPLATE:
        //CREATE TABLE IF NOT EXISTS incident_table (name VARCHAR(255), description VARCHAR(255), PRIMARY KEY (name));
        String createIncidentTable = String.format("CREATE TABLE IF NOT EXISTS %1$s (%2$s VARCHAR(255), %3$s VARCHAR(255), %4$s VARCHAR(500), PRIMARY KEY(%5$s));",
                INCIDENT_TABLE, NAME_COLUMN, DESCRIPTION_COLUMN, INCIDENT_WEATHER, NAME_COLUMN);
        //Build string for creating the image_table table
        //TEMPLATE:
        //CREATE TABLE IF NOT EXISTS image_table (picture_reference INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR(255), picture BLOB);
        String createPictureTable = String.format("CREATE TABLE IF NOT EXISTS %1$s ( %2$s INTEGER PRIMARY KEY AUTOINCREMENT, %3$s VARCHAR(255), %4$s BLOB);",
                PICTURE_TABLE, PICTURE_PRIMARY_COLUMN, PICTURE_NAME_COLUMN, PICTURE_COLUMN, PICTURE_PRIMARY_COLUMN);
        try{
            db.execSQL(createIncidentTable); //Add incident table to DB
            db.execSQL(createPictureTable);   //Add picture table to DB
        }catch(Exception e){
            throw e;
        }
    }

    /**
     * Takes an incident object and parses the information stored in it to add it to the database
     * @param incident Incident object that contains all information needed for the database entry
     *                 (String name, String description, String weather, List<Bitmap> images)
     */
    public void addIncident(Incident incident){
        //TEMPLATE:
        //INSERT INTO incident_table VALUES ('incident.getName()', 'incident.getDescription()');
        //Also written as:
        //INSERT INTO incident_table VALUES (incident.toString());
        //Doesn't include the pictures
        String insertIncidentTable = String.format("INSERT INTO %1$s VALUES ('%2$s', '%3$s', '%4$s');",
                INCIDENT_TABLE, incident.getName(), incident.getDescription(), incident.getWeather());
        List<Bitmap> images = incident.getImages();
        try{
            db.execSQL(insertIncidentTable);
            //Add pictures to picture table here
            for(int i = 0; i < images.size(); i++) {
                ContentValues imageNameInsert = new ContentValues();
                //Associate the name of incident to the column that holds name in image_table
                imageNameInsert.put(PICTURE_NAME_COLUMN, incident.getName());
                //Associate the picture with the picture column in the image_table
                imageNameInsert.put(PICTURE_COLUMN, imageToByte(images.get(i)));
                //Add the name and picture to the image_table
                db.insert(PICTURE_TABLE, null, imageNameInsert);
            }
        }catch(Exception e){
            System.out.println("Error adding incident to table: " + e.getMessage());
            throw e;
        }
    }


    /**
     * Takes a new incident object and updates the current version of that incident in the database with
     * the new incident's values.
     * @param incident Incident that contains the new values for the current incident
     * @param originalName String that is the name of the incident that is currently in the database
     */
    public void updateIncident(Incident incident, String originalName){
        //TEMPLATE:
        //UPDATE incident_table SET description = 'incident.getDescription()' WHERE name = 'incident.getName()';
        String updateIncident = String.format("UPDATE %1$s SET description = '%2$s', name = '%3$s', weather = '%4$s' WHERE name = '%5$s';",
                INCIDENT_TABLE, incident.getDescription(), incident.getName(), incident.getWeather(), originalName);
        //Remove current pictures from table corresponding to originalName so new pictures can be added,
        //if image list is the same, the original images will be added
        String deletePictures = String.format("DELETE FROM %1$s WHERE name = '%2$s';",
                PICTURE_TABLE, originalName);
        try {
            db.execSQL(updateIncident); //update values in incident_table
            db.execSQL(deletePictures);  //remove pictures from picture table
            //Add updated picture list to picture table
            List<Bitmap> images = incident.getImages();
            for(int i = 0; i < images.size(); i++){
                byte[] byteImage = imageToByte(images.get(i));
                ContentValues pictureInsert = new ContentValues();
                pictureInsert.put(PICTURE_NAME_COLUMN, incident.getName());
                pictureInsert.put(PICTURE_COLUMN, byteImage);
                db.insert(PICTURE_TABLE, null, pictureInsert);
            }

        }catch(Exception e){
            throw e;
        }
    }

    /**
     * Removes all values from the incident with the passed in name from the database
     * @param name String the name of the incident to remove from database
     */
    public void removeIncident(String name){
        //TEMPLATE:
        //DELETE FROM incident_table WHERE name='incident.getName()';
        String deleteIncident = String.format("DELETE FROM %1$s WHERE name = '%2$s';", INCIDENT_TABLE, name);
        String deletePictures = String.format("DELETE FROM %1$s WHERE name = '%2$s';", PICTURE_TABLE, name);
        try{
            db.execSQL(deleteIncident); //Remove incident from incident_table
            db.execSQL(deletePictures); //Remove incident from image_table
        }catch(Exception e){
            throw e;
        }
    }

    /**
     * Given a name of an incident, gets all the values corresponding to that incident and creates an incident
     * containing all corresponding data and returns it
     * @param name String to search for in the database
     * @return Incident containing corresponding values based on the name provided
     */
    public Incident getIncident(String name){
        Cursor incidentCursor = null;   //Holds query results from incident_table
        Cursor imageTableCursor = null; //Holds query results form image_table
        String incidentQuery = String.format("SELECT * FROM %1$s WHERE name = '%2$s';", INCIDENT_TABLE, name);
        String imageQuery = String.format("SELECT * FROM %1$s WHERE name = '%2$s';", PICTURE_TABLE, name);
        Incident incident;
        try{
            incidentCursor = db.rawQuery(incidentQuery, null);
            incidentCursor.moveToFirst();
            //Get numeric value for the different columns in the incident_table
            int incidentNameIndex = incidentCursor.getColumnIndex(NAME_COLUMN);
            int incidentDescriptionIndex = incidentCursor.getColumnIndex(DESCRIPTION_COLUMN);
            int incidentWeatherIndex = incidentCursor.getColumnIndex(INCIDENT_WEATHER);

            //Create and incident and add everything but the images here
            incident = new Incident();
            incident.setName(incidentCursor.getString(incidentNameIndex));
            incident.setDescription(incidentCursor.getString(incidentDescriptionIndex));
            incident.setWeather(incidentCursor.getString(incidentWeatherIndex));

            //Make object to hold the images
            imageTableCursor = db.rawQuery(imageQuery, null);   //Get the images for incident
            int imagePictureIndex = imageTableCursor.getColumnIndex(PICTURE_COLUMN);
            List<Bitmap> images = new ArrayList<Bitmap>();
            imageTableCursor.moveToFirst();
            //Add all the bitmaps to a list for the incident
            for(int i = 0; i < imageTableCursor.getCount(); i++){
                byte[] blobImage = imageTableCursor.getBlob(imagePictureIndex);
                Bitmap bMap = BitmapFactory.decodeByteArray(blobImage, 0, blobImage.length);
                images.add(bMap);
            }
            //Close the cursors to prevent memory leaks
            imageTableCursor.close();
            incidentCursor.close();
            incident.setImages(images);
            return incident;
        }catch(Exception e){
            //Add toast displaying error here
            System.out.println("Error getting incident " + name + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Returns a list of all the incidents that are stored in the database
     * @return List<Incident> of all incidents in the database
     */
    public List<Incident> getAllIncidents(){
        List<Incident> allIncidents = new ArrayList<Incident>();
        //Object to hold the incident_table query results
        Cursor incidentQueryResults = null;
        //Get numeric values for the columns in the incident_table
        int nameIndex;
        int descriptionIndex;
        int weatherIndex;
        //Object to hold the pictures
        Cursor pictureCursor = null;
        String selectAllQuery = String.format("SELECT * FROM %s;", INCIDENT_TABLE);
        try{
            incidentQueryResults = db.rawQuery(selectAllQuery, null);
            //Get numeric values for the columns in the incident_table
            nameIndex = incidentQueryResults.getColumnIndex(NAME_COLUMN);
            descriptionIndex = incidentQueryResults.getColumnIndex(DESCRIPTION_COLUMN);
            weatherIndex = incidentQueryResults.getColumnIndex(INCIDENT_WEATHER);
            incidentQueryResults.moveToFirst();
            //Get name, description, and weather from incident table
            for(int i = 0; i < incidentQueryResults.getCount(); i++){
                //Get the info from the incident_table and store them in Strings to be added to incident later
                String name = incidentQueryResults.getString(nameIndex);
                String weather = incidentQueryResults.getString(weatherIndex);
                //Make query to get pictures
                String selectPicturesQuery = String.format("SELECT %1$s FROM %2$s WHERE name = '%3$s';",
                        PICTURE_COLUMN, PICTURE_TABLE, name);
                pictureCursor = db.rawQuery(selectPicturesQuery, null);
                pictureCursor.moveToFirst();
                List<Bitmap> images = new ArrayList<Bitmap>();
                int imageCount = pictureCursor.getCount();
                //Iterate through pictures from cursor and add them to a List<Bitmap>
                for(int j = 0; j < pictureCursor.getCount(); j++){
                    byte[] imageBytes = pictureCursor.getBlob(j);
                    Bitmap bitImage = byteToImage(imageBytes);
                    images.add(bitImage);
                    pictureCursor.moveToNext();
                }
                pictureCursor.close();  //Close cursor to prevent memory leak
                //Create new incident and add values from database to it
                Incident incident = new Incident();
                incident.setName(name);
                incident.setDescription(incidentQueryResults.getString(descriptionIndex));
                incident.setWeather(weather);
                incident.setImages(images);
                allIncidents.add(incident);
                incidentQueryResults.moveToNext();
            }
            incidentQueryResults.close(); //Close cursor to prevent memory leak
        }catch(Exception e){
            //Add toast displaying error here
            System.out.println("Error getting all incidents: " + e.getMessage());
        }
        return allIncidents;
    }

    /**
     * Converts Bitmap image into byte[] representation
     * @param image Bitmap of the image that needs to be converted to a byte[]
     * @return byte[] representation of the passed in Bitmap
     */
    private byte[] imageToByte(Bitmap image){
        ByteArrayOutputStream oStream = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.PNG, 50, oStream);
        return oStream.toByteArray();
    }

    /**
     * Converts a byte[] into a Bitmap representation of the image
     * @param bImage byte[] of the image that needs to be converted to a Bitmap
     * @return Bitmap representation of the passed in byte[]
     */
    private Bitmap byteToImage(byte[] bImage){
        return BitmapFactory.decodeByteArray(bImage, 0, bImage.length);
    }

    //FOR DEBUGGING ONLY
    public void dropAllTables(){
        String dropIncidentTable = "DROP TABLE " + INCIDENT_TABLE;
        String dropImageTable = "DROP TABLE " + PICTURE_TABLE;
        try{
            db.execSQL(dropIncidentTable);
            System.out.println("Dropped incident table");
            db.execSQL(dropImageTable);
            System.out.println("Dropped picture table");
        }catch(Exception e){
            System.out.println("Error dropping tables: " + e.getMessage());
        }
    }

    //FOR DEBUGGING ONLY
    public int getImageCount(){
        int size = -1;
        Cursor countCursor;
        try{
            countCursor = db.rawQuery("SELECT * FROM image_table;", null);
            size = countCursor.getCount();
        }catch(Exception e){
            System.out.println("Error getting count: " + e.getMessage());
        }
        return size;
    }

    //FOR DEBUGGING ONLY
    public int getIncidentCount(){
        int size = -1;
        Cursor countCursor;
        try{
            countCursor = db.rawQuery("SELECT * FROM incident_table;", null);
            size = countCursor.getCount();
        }catch(Exception e){
            System.out.println("Error getting count of incidents");
        }
        return size;
    }

    //FOR DEBUGGING ONLY
    public void removeAllRows(){
        String removeIncidentRows = String.format("DELETE FROM %1$s;", INCIDENT_TABLE);
        String removePictureRows = String.format("DELETE FROM %1$s;", PICTURE_TABLE);
        try{
            db.execSQL(removeIncidentRows);
            db.execSQL(removePictureRows);
        }catch(Exception e){
            System.out.println("Error deleting all rows: " + e.getMessage());
        }

    }
}