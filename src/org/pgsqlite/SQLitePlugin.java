/*
 * PhoneGap is available under *either* the terms of the modified BSD license *or* the
 * MIT License (2008). See http://opensource.org/licenses/alphabetical for full text.
 *
 * Copyright (c) 2005-2010, Nitobi Software Inc.
 * Copyright (c) 2010, IBM Corporation
 */
package org.pgsqlite;

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
	 * Multiple database map (static).
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
	 * NOTE: Using default constructor, explicit constructor no longer required.
	 */

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

				JSONArray jparams = (args.length() < 3) ? null : args.getJSONArray(2);

				String[] params = null;

				if (jparams != null) {
					params = new String[jparams.length()];

					for (int j = 0; j < jparams.length(); j++) {
						if (jparams.isNull(j))
							params[j] = "";
						else
							params[j] = jparams.getString(j);
					}
				}

				Cursor myCursor = this.getDatabase(dbName).rawQuery(query, params);

				String result = this.getRowsResultFromQuery(myCursor).getJSONArray("rows").toString();

				this.sendJavascriptCB("window.SQLitePluginCallback.p1('" + id + "', " + result + ");");
			}
			**/
			else if (action.equals("executeSqlBatch") || action.equals("executeBatchTransaction") || action.equals("backgroundExecuteSqlBatch"))
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
					boolean ex = action.equals("executeBatchTransaction");

					if (action.equals("backgroundExecuteSqlBatch"))
						this.executeSqlBatchInBackground(dbName, queries, jsonparams, queryIDs, trans_id, ex);
					else
						this.executeSqlBatch(dbName, queries, jsonparams, queryIDs, trans_id, ex);
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
		if (this.getDatabase(dbname) != null) this.closeDatabase(dbname);

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
		final String[] queryarr, final JSONArray[] jsonparams, final String[] queryIDs, final String tx_id, final boolean ex)
	{
		final SQLitePlugin myself = this;

		this.cordova.getThreadPool().execute(new Runnable() {
			public void run() {
				myself.executeSqlBatch(dbName, queryarr, jsonparams, queryIDs, tx_id, ex);
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
	private void executeSqlBatch(String dbname, String[] queryarr, JSONArray[] jsonparams, String[] queryIDs, String tx_id, /* TODO GONE: */ boolean exc)
	{
		Long db = this.getDatabase(dbname);

		if (db == null) return;

		long mydb = db.longValue();

		String query = "";
		String query_id = "";
		int len = queryarr.length;

		for (int i = 0; i < len; i++) {
			try {
				query = queryarr[i];
				query_id = queryIDs[i];

				String query_result = "";

				int step_return = 0;

				// XXX TODO bindings for UPDATE & rowsAffected for UPDATE/DELETE/INSERT
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

					int rowsAffected = myStatement.executeUpdateDelete();

					query_result = "{'rowsAffected':" + rowsAffected + "}";
				} else // to HERE. */
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

					step_return = SQLiteGlue.sqlg_st_step(st);

					// XXX TODO get insertId

					SQLiteGlue.sqlg_st_finish(st);

					//query_result = "{'insertId':'" + insertId + "', 'rowsAffected':'" + rowsAffected +"'}";
					query_result = "{'rowsAffected':1}";
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

					step_return = SQLiteGlue.sqlg_st_step(st);

					if ((step_return == 100) && query_id.length() > 0)
						query_result = this.getRowsResultFromQuery(st).toString();
					else if (query_id.length() > 0) {
						query_result = "{}";
					}

					//SQLiteGlue.sqlg_st_finish(st);
				}

				if (step_return != 0 && step_return < 100) {
					this.sendJavascriptCB("window.SQLiteQueryCB.queryErrorCallback('" +
						tx_id + "','" + query_id + "', '" + "query failure" + "');");
				}
				else
				if (query_result.length() > 0) {
					this.sendJavascriptCB("window.SQLiteQueryCB.queryCompleteCallback('" +
						tx_id + "','" + query_id + "', " + query_result + ");");
				}
			} catch (Exception ex) {
				ex.printStackTrace();
				Log.v("executeSqlBatch", "SQLitePlugin.executeSql[Batch](): Error=" +  ex.getMessage());
				this.sendJavascriptCB("window.SQLiteQueryCB.queryErrorCallback('" +
					tx_id + "','" + query_id + "', '" + ex.getMessage() + "');");
			}
		}
	}

	/**
	 * Get rows results from query [TBD XXX].
	 *
	 * @param cur
	 *            [TBD XXX] Cursor into query results
	 *
	 * @return results in [TBD XXX] form
	 *
	 */
	private JSONObject getRowsResultFromQuery(long st)
	{
		JSONObject rowsResult = new JSONObject();

		// If query result has rows
		//if (cur.moveToFirst())
		{
			JSONArray rowsArrayResult = new JSONArray();

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

					rowsArrayResult.put(row);

				} catch (JSONException e) {
					e.printStackTrace();
				}

			} while (SQLiteGlue.sqlg_st_step(st) == 100); /* SQLITE_ROW */

			try {
				rowsResult.put("rows", rowsArrayResult);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}

		return rowsResult;
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
		this.webView.sendJavascript(cb);
	}

	/**
	 * Send Javascript callback on GUI thread.
	 *
	 * @param cb
	 *            Javascript callback command to send
	 *
	 */
	private void sendJavascriptToGuiThread(final String cb)
	{
		final SQLitePlugin myself = this;

		this.cordova.getActivity().runOnUiThread(new Runnable() {
			public void run() {
				myself.webView.sendJavascript(cb);
			}
		});
	}
}
