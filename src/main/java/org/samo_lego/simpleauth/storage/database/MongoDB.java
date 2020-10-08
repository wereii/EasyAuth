package org.samo_lego.simpleauth.storage.database;


import com.google.gson.JsonObject;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOneModel;
import org.bson.Document;
import org.iq80.leveldb.DBException;
import org.samo_lego.simpleauth.storage.PlayerCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static org.samo_lego.simpleauth.SimpleAuth.config;
import static org.samo_lego.simpleauth.utils.SimpleLogger.logError;

public class MongoDB {
    private static MongoCollection<Document> collection;
    private static MongoClient mongoClient;

    public static void initialize() {
        /*mongoClient = MongoClients.create(
                String.format(
                        "mongodb://%s:%s@%s:%s/?authSource=db1&ssl=%s",
                        config.mongoDBCredentials.username,
                        config.mongoDBCredentials.password,
                        config.mongoDBCredentials.host,
                        config.mongoDBCredentials.port,
                        config.mongoDBCredentials.useSsl

                )
        );*/
        mongoClient = MongoClients.create(String.format("mongodb://%s:%s", config.mongoDBCredentials.host, config.mongoDBCredentials.port));
        MongoDatabase database = mongoClient.getDatabase(config.mongoDBCredentials.databaseName);
        collection = database.getCollection("players");
    }

    public static boolean isUserRegistered(String uuid) {
        try {
            return collection.find(eq("UUID", uuid)).iterator().hasNext();
        } catch (DBException e) {
            logError(e.getMessage());
        }
        return false;
    }

    public static boolean registerUser(String uuid, String data) {
        if(!isUserRegistered(uuid)) {
            collection.insertOne(
                    new Document(uuid, data)
            );
            return true;
        }
        return false;
    }

    public static void deleteUserData(String uuid) {
        collection.deleteOne(eq("UUID", uuid));
    }

    public static void updateUserData(String uuid, String data) {
        collection.updateOne(eq("UUID", uuid), new Document(uuid, data));
    }

    public static String getUserData(String uuid){
        return isUserRegistered(uuid) ? collection.find(eq("UUID", uuid)).iterator().next().getString(uuid) : "";
    }

    public static void saveFromCache(HashMap<String, PlayerCache> playerCacheMap) {
        List<ReplaceOneModel<Document>> list = new ArrayList<>();
        playerCacheMap.forEach((uuid, playerCache) -> {
            JsonObject data = playerCache.toJson();
            list.add(new ReplaceOneModel<>(eq("UUID", uuid), new Document(uuid, data.toString())));
        });

        collection.bulkWrite(list);
    }

    public static boolean close() {
        mongoClient.close();
        return true;
    }
}
