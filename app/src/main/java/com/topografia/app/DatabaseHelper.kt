package com.topografia.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "TopografiaDB", null, 2) { // Versão 2 para atualizar a tabela

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE Pontos ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "nome TEXT,"
                + "norte REAL,"
                + "leste REAL,"
                + "cota REAL,"
                + "zona TEXT,"
                + "status TEXT,"
                + "projeto TEXT)") // NOVA COLUNA DE PROJETO
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Como estamos em fase de testes, se a versão mudar, ele recria a tabela limpa
        db.execSQL("DROP TABLE IF EXISTS Pontos")
        onCreate(db)
    }

    fun inserirPonto(ponto: PontoTopografico) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put("nome", ponto.nome)
        values.put("norte", ponto.norteUtm)
        values.put("leste", ponto.lesteUtm)
        values.put("cota", ponto.cotaChao)
        values.put("zona", ponto.zonaUtm)
        values.put("status", ponto.statusRtk)
        values.put("projeto", ponto.nomeProjeto) // Salva o nome do projeto junto
        db.insert("Pontos", null, values)
        db.close()
    }

    // Busca apenas os pontos daquele projeto específico!
    fun buscarPontosPorProjeto(nomeDoProjeto: String): MutableList<PontoTopografico> {
        val lista = mutableListOf<PontoTopografico>()
        val db = this.readableDatabase
        
        // Pesquisa no banco filtrando pela coluna 'projeto'
        val cursor = db.rawQuery("SELECT * FROM Pontos WHERE projeto = ?", arrayOf(nomeDoProjeto))
        
        if (cursor.moveToFirst()) {
            do {
                val ponto = PontoTopografico(
                    cursor.getString(cursor.getColumnIndexOrThrow("nome")),
                    cursor.getDouble(cursor.getColumnIndexOrThrow("norte")),
                    cursor.getDouble(cursor.getColumnIndexOrThrow("leste")),
                    cursor.getDouble(cursor.getColumnIndexOrThrow("cota")),
                    cursor.getString(cursor.getColumnIndexOrThrow("zona")),
                    cursor.getString(cursor.getColumnIndexOrThrow("status")),
                    cursor.getString(cursor.getColumnIndexOrThrow("projeto"))
                )
                lista.add(ponto)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return lista
    }
}
