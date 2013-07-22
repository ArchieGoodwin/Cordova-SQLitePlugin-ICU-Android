/*
 * PhoneGap is available under *either* the terms of the modified BSD license *or* the
 * MIT License (2008). See http://opensource.org/licenses/alphabetical for full text.
 *
 * Copyright (c) 2005-2010, Nitobi Software Inc.
 * Copyright (c) 2010, IBM Corporation
 */
package com.phonegap.plugin.sqlitePlugin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

import java.lang.Number;

import java.util.HashMap;

import org.apache.cordova.api.CordovaPlugin;
import org.apache.cordova.api.CallbackContext;

import org.sqlg.SQLiteGlue;

import android.util.Base64;
import android.util.Log;

public class SQLitePlugin extends CordovaPlugin
{
	/**
	 * Multiple database map.
	 */
	static HashMap<String, Long> dbmap = new HashMap<String, Long>();

	static {
		System.loadLibrary("sqlg");
	}

	/**
	 * Get a SQLiteGlue database reference from the db map (public static accessor).
	 *
	 * @param dbname
	 *            The name of the database.
	 *
	 */
	public static long getSQLiteGlueDatabase(String dbname)
	{
		return dbmap.get(dbname);
	}

	/**
         * XXX GONE:
	 * Constructor.
	public SQLitePlugin() {
		// XXX TBD move to static section:
		if (dbmap == null) {
			dbmap = new HashMap<String, Long>();
		}
	} */

	/**
	 * Executes the request and returns PluginResult.
	 *
	 * @param action
	 *            The action to execute.
	 *
	 * @param args
	 *            JSONArry of arguments for the plugin.
	 *
	 * @param cbc
	 *            Callback context from Cordova API (not used here)
	 *
	 */
	@Override
	public boolean execute(String action, JSONArray args, CallbackContext cbc)
	{
		try {
			if (action.equals("open")) {
				JSONObject o = args.getJSONObject(0);
				String dbname = o.getString("name");

				return this.openDatabase(dbname, null);
			}
			else if (action.equals("close")) {
				this.closeDatabase(args.getString(0));
			}
			/**
			else if (action.equals("executePragmaStatement"))
			{
				String dbName = args.getString(0);
				String query = args.getString(1);

				Cursor myCursor = getDatabase(dbName).rawQuery(query, null);
				this.processPragmaResults(myCursor, id);
			}
			**/
			else if (action.equals("executeSqlBatch"))
			{
				String[] 	queries 	= null;
				String[] 	queryIDs 	= null;
				String 		trans_id 	= null;
				JSONObject 	a 			= null;
				JSONArray 	jsonArr 	= null;
				int 		paramLen	= 0;
				JSONArray[] 	jsonparams 	= null;

				String dbName = args.getString(0);
				JSONArray txargs = args.getJSONArray(1);

				if (txargs.isNull(0)) {
					queries = new String[0];
				} else {
					int len = txargs.length();
					queries = new String[len];
					queryIDs = new String[len];
					jsonparams = new JSONArray[len];

					for (int i = 0; i < len; i++)
					{
						a 			= txargs.getJSONObject(i);
						queries[i] 	= a.getString("query");
						queryIDs[i] = a.getString("query_id");
						trans_id 	= a.getString("trans_id");
						jsonArr 	= a.getJSONArray("params");
						paramLen	= jsonArr.length();
						jsonparams[i] 	= jsonArr;
					}
				}
				if(trans_id != null) {
					if (false) // XXX FUTURE use parameter
						this.executeSqlBatchInBackground(dbName, queries, jsonparams, queryIDs, trans_id);
					else
						this.executeSqlBatch(dbName, queries, jsonparams, queryIDs, trans_id);
				} else
					Log.v("error", "null trans_id");
			}

			return true;
		} catch (JSONException e) {
			// TODO: signal JSON problem to JS

			return false;
		}
	}

	/**
	 *
	 * Clean up and close all open databases.
	 *
	 */
	@Override
	public void onDestroy() {
		while (!dbmap.isEmpty()) {
			String dbname = dbmap.keySet().iterator().next();
			this.closeDatabase(dbname);
			dbmap.remove(dbname);
		}
	}

	// --------------------------------------------------------------------------
	// LOCAL METHODS
	// --------------------------------------------------------------------------

	/**
	 * Open a database.
	 *
	 * @param dbname
	 *            The name of the database-NOT including its extension.
	 *
	 * @param password
	 *            The database password or null.
	 *
	 */
	private boolean openDatabase(String dbname, String password) //throws SQLiteException
	{
		if (getDatabase(dbname) != null) this.closeDatabase(dbname);

		String dbfilepath = this.cordova.getActivity().getDatabasePath(dbname + ".db").getAbsolutePath();

		Log.v("info", "Open dbfilepath: " + dbfilepath);

		long mydb = SQLiteGlue.sqlg_db_open(dbfilepath, SQLiteGlue.SQLG_OPEN_READWRITE | SQLiteGlue.SQLG_OPEN_CREATE);

		if (mydb < 0) return false;

		dbmap.put(dbname, mydb);

		return true;
	}

	/**
	 * Close a database.
	 *
	 * @param dbName
	 *            The name of the database-NOT including its extension.
	 *
	 */
	private void closeDatabase(String dbName)
	{
		Long mydb = this.getDatabase(dbName);

		if (mydb != null)
		{
			SQLiteGlue.sqlg_db_close(mydb.longValue());
			dbmap.remove(dbName);
		}
	}

	/**
	 * Get a database from the db map.
	 *
	 * @param dbname
	 *            The name of the database.
	 *
	 */
	private Long getDatabase(String dbname)
	{
		return dbmap.get(dbname);
	}

	/**
	 * Executes a batch request IN BACKGROUND THREAD and sends the results via sendJavascriptCB().
	 *
	 * @param dbName
	 *            The name of the database.
	 *
	 * @param queryarr
	 *            Array of query strings
	 *
	 * @param jsonparams
	 *            Array of JSON query parameters
	 *
	 * @param queryIDs
	 *            Array of query ids
	 *
	 * @param tx_id
	 *            Transaction id
	 *
	 */
	private void executeSqlBatchInBackground(final String dbName,
		final String[] queryarr, final JSONArray[] jsonparams, final String[] queryIDs, final String tx_id)
	{
		final SQLitePlugin myself = this;

		this.cordova.getThreadPool().execute(new Runnable() {
			public void run() {
				myself.executeSqlBatch(dbName, queryarr, jsonparams, queryIDs, tx_id);
			}
		});
	}

	/**
	 * Executes a batch request and sends the results via sendJavascriptCB().
	 *
	 * @param dbname
	 *            The name of the database.
	 *
	 * @param queryarr
	 *            Array of query strings
	 *
	 * @param jsonparams
	 *            Array of JSON query parameters
	 *
	 * @param queryIDs
	 *            Array of query ids
	 *
	 * @param tx_id
	 *            Transaction id
	 *
	 */
	private void executeSqlBatch(String dbname, String[] queryarr, JSONArray[] jsonparams, String[] queryIDs, String tx_id)
	{
		Long db = this.getDatabase(dbname);

		if (db == null) return;

		long mydb = db.longValue();

		try {
			// XXX TODO:
			//mydb.beginTransaction();

			String query = "";
			String query_id = "";
			int len = queryarr.length;

			for (int i = 0; i < len; i++) {
				query = queryarr[i];
				query_id = queryIDs[i];

				/**
				// /* OPTIONAL changes for new Android SDK from HERE:
				if (android.os.Build.VERSION.SDK_INT >= 11 &&
				    (query.toLowerCase().startsWith("update") ||
				     query.toLowerCase().startsWith("delete")))
				{
					//SQLiteStatement myStatement = mydb.compileStatement(query);
					SQLiteStatement myStatement = mydb.prepare(query);

					if (jsonparams != null) {
						for (int j = 0; j < jsonparams[i].length(); j++) {
							if (jsonparams[i].get(j) instanceof Float || jsonparams[i].get(j) instanceof Double ) {
								myStatement.bind(j + 1, jsonparams[i].getDouble(j));
							} else if (jsonparams[i].get(j) instanceof Number) {
								myStatement.bind(j + 1, jsonparams[i].getLong(j));
							} else if (jsonparams[i].isNull(j)) {
								myStatement.bindNull(j + 1);
							} else {
								myStatement.bind(j + 1, jsonparams[i].getString(j));
							}
						}
					}
					//int rowsAffected = myStatement.executeUpdateDelete();

					myStatement.step();


					//String result = "{'rowsAffected':" + rowsAffected + "}";
					String result = "{}"; // XXX TODO
					myStatement.dispose();
					this.sendJavascriptCB("window.SQLitePluginTransactionCB.queryCompleteCallback('" +
						tx_id + "','" + query_id + "', " + result + ");");
				} else // to HERE.
				**/ // XXX TODO bindings for UPDATE & rowsAffected for UPDATE/DELETE/INSERT
				if (query.toLowerCase().startsWith("insert") && jsonparams != null) {
					/* prepare/compile statement: */
					long st = SQLiteGlue.sqlg_db_prepare_st(mydb, query);

					for (int j = 0; j < jsonparams[i].length(); j++) {
						if (jsonparams[i].get(j) instanceof Float || jsonparams[i].get(j) instanceof Double ) {
							SQLiteGlue.sqlg_st_bind_double(st, j + 1, jsonparams[i].getDouble(j));
						} else if (jsonparams[i].get(j) instanceof Number) {
							SQLiteGlue.sqlg_st_bind_int64(st, j + 1, jsonparams[i].getLong(j));
						// XXX TODO bind null:
						//} else if (jsonparams[i].isNull(j)) {
						//	myStatement.bindNull(j + 1);
						} else {
							SQLiteGlue.sqlg_st_bind_text(st, j + 1, jsonparams[i].getString(j));
						}
					}

					int r1 = SQLiteGlue.sqlg_st_step(st);

					// XXX TODO get insertId

					//String result = "{'insertId':'" + insertId + "', 'rowsAffected':1}";
					String result = "{'rowsAffected':1}";

					SQLiteGlue.sqlg_st_finish(st);

					this.sendJavascriptCB("window.SQLitePluginTransactionCB.queryCompleteCallback('" +
						tx_id + "','" + query_id + "', " + result + ");");
				} else {
					long st = SQLiteGlue.sqlg_db_prepare_st(mydb, query);

					if (jsonparams != null) {
						for (int j = 0; j < jsonparams[i].length(); j++) {
							//if (jsonparams[i].isNull(j))
								//params[j] = "";
								//myStatement.bindNull(j + 1);
							//else
								//params[j] = jsonparams[i].getString(j);
								//myStatement.bind(j + 1, jsonparams[i].getString(j));
								SQLiteGlue.sqlg_st_bind_text(st, j + 1, jsonparams[i].getString(j));
						}
					}

					int r1 = SQLiteGlue.sqlg_st_step(st);

					if ((r1 == 100) && query_id.length() > 0)
						this.processResults(st, query_id, tx_id);
					else if (query_id.length() > 0) {
						String result = "[]";
						this.sendJavascriptCB("window.SQLitePluginTransactionCB.queryCompleteCallback('" +
						tx_id + "','" + query_id + "', " + result + ");");
					}

					//SQLiteGlue.sqlg_st_finish(st);
				}
			}
			// XXX TODO:
			//mydb.setTransactionSuccessful();
		}
		/**
		catch (SQLiteException ex) {
			ex.printStackTrace();
			Log.v("executeSqlBatch", "SQLitePlugin.executeSql(): Error=" +  ex.getMessage());
			this.sendJavascriptCB("window.SQLitePluginTransactionCB.txErrorCallback('" + tx_id + "', '"+ex.getMessage()+"');");
		} **/
		catch (JSONException ex) {
			ex.printStackTrace();
			Log.v("executeSqlBatch", "SQLitePlugin.executeSql(): Error=" +  ex.getMessage());
			this.sendJavascriptCB("window.SQLitePluginTransactionCB.txErrorCallback('" + tx_id + "', '"+ex.getMessage()+"');");
		}
		finally {
			// XXX TODO:
			//mydb.endTransaction();
			Log.v("executeSqlBatch", tx_id);
			this.sendJavascriptCB("window.SQLitePluginTransactionCB.txCompleteCallback('" + tx_id + "');");
		}
	}

	/**
	 * Process query results.
	 *
	 * @param cur
	 *            Cursor into query results
	 *
	 * @param query_id
	 *            Query id
	 *
	 * @param tx_id
	 *            Transaction id
	 *
	 */
	private void processResults(long st, String query_id, String tx_id) //throws SQLiteException
	{
		String result = this.results2string(st);

		SQLiteGlue.sqlg_st_finish(st);

		this.sendJavascriptCB("window.SQLitePluginTransactionCB.queryCompleteCallback('" +
			tx_id + "','" + query_id + "', " + result + ");");
	}

	/**
	 * Process query results.
	 *
	 * @param cur
	 *            Cursor into query results
	 *
	 * @param id
	 *            Caller db id
	 *
	 */
/**
	private void processPragmaResults(Cursor cur, String id)
	{
		String result = this.results2string(cur);

		this.sendJavascriptCB("window.SQLitePluginCallback.p1('" + id + "', " + result + ");");
	}
**/

	/**
	 * Convert results cursor to JSON string.
	 *
	 * @param cur
	 *            Cursor into query results
	 *
	 * @return results in string form
	 *
	 */
	private String results2string(long st) //throws SQLiteException
	{
		String result = "[]";

		// If query result has rows
		//if (cur.moveToFirst()) {
			JSONArray fullresult = new JSONArray();
			String key = "";

			int colCount = SQLiteGlue.sqlg_st_column_count(st);

			// Build up JSON result object for each row
			do {
				JSONObject row = new JSONObject();
				try {
					for (int i = 0; i < colCount; ++i) {
						key = SQLiteGlue.sqlg_st_column_name(st, i);

						/* // for old Android SDK remove lines from HERE:
						if(android.os.Build.VERSION.SDK_INT >= 11)
						{
							switch(cur.getType (i))
							{
								case Cursor.FIELD_TYPE_NULL:
									row.put(key, JSONObject.NULL);
									break;
								case Cursor.FIELD_TYPE_INTEGER:
									row.put(key, cur.getInt(i));
									break;
								case Cursor.FIELD_TYPE_FLOAT:
									row.put(key, cur.getFloat(i));
									break;
								case Cursor.FIELD_TYPE_STRING:
									row.put(key, cur.getString(i));
									break;
								case Cursor.FIELD_TYPE_BLOB:
									row.put(key, new String(Base64.encode(cur.getBlob(i), Base64.DEFAULT)));
									break;
							}
						}
						else // to HERE.*/
						{
							row.put(key, SQLiteGlue.sqlg_st_column_text(st, i));
						}
					}

					fullresult.put(row);

				} catch (JSONException e) {
					e.printStackTrace();
				}

			} while (SQLiteGlue.sqlg_st_step(st) == 100); /* SQLITE_ROW */

			result = fullresult.toString();
		//}

		return result;
	}

	/**
	 * Send Javascript callback.
	 *
	 * @param cb
	 *            Javascript callback command to send
	 *
	 */
	private void sendJavascriptCB(String cb)
	{
		//this.webView.sendJavascript(cb);
		final String mycb = cb;
		final SQLitePlugin myself = this;

		this.cordova.getActivity().runOnUiThread(new Runnable() {
			public void run() {
				myself.webView.sendJavascript(mycb);
			}
		});
	}
}
