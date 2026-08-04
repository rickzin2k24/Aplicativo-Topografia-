package com.topografia.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "TopografiaDB", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        // Cria a tabela blindada onde os pontos vão morar
        val createTable = ("CREATE TABLE Pontos ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "nome TEXT,"
                + "norte REAL,"
                + "leste REAL,"
                + "cota REAL,"
                + "zona TEXT,"
                + "status TEXT)")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS Pontos")
        onCreate(db)
    }

    // Função que salva o ponto na memória física
    fun inserirPonto(ponto: PontoTopografico) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put("nome", ponto.nome)
        values.put("norte", ponto.norteUtm)
        values.put("leste", ponto.lesteUtm)
        values.put("cota", ponto.cotaChao)
        values.put("zona", ponto.zonaUtm)
        values.put("status", ponto.statusRtk)
        db.insert("Pontos", null, values)
        db.close()
    }

    // Função que resgata todos os pontos quando você liga o app
    fun buscarTodosPontos(): MutableList<PontoTopografico> {
        val lista = mutableListOf<PontoTopografico>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM Pontos", null)
        
        if (cursor.moveToFirst()) {
            do {
                val ponto = PontoTopografico(
                    cursor.getString(cursor.getColumnIndexOrThrow("nome")),
                    cursor.getDouble(cursor.getColumnIndexOrThrow("norte")),
                    cursor.getDouble(cursor.getColumnIndexOrThrow("leste")),
                    cursor.getDouble(cursor.getColumnIndexOrThrow("cota")),
                    cursor.getString(cursor.getColumnIndexOrThrow("zona")),
                    cursor.getString(cursor.getColumnIndexOrThrow("status"))
                )
                lista.add(ponto)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return lista
    }
}
