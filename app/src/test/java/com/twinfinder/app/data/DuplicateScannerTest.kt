package com.twinfinder.app.data

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Auditoria final (05/11/2026): o app apaga fotos do usuário e não tinha NENHUM teste. O scanner decide o que
 * é "cópia idêntica"; um erro aqui manda para a lixeira um arquivo que não era igual.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DuplicateScannerTest {

    @get:Rule
    val pasta = TemporaryFolder()

    private val scanner by lazy { DuplicateScanner(RuntimeEnvironment.getApplication()) }

    private fun arquivo(nome: String, conteudo: ByteArray): File = pasta.newFile(nome).apply { writeBytes(conteudo) }

    private fun media(f: File, id: Long, data: Long, tamanho: Long = f.length()) = Media(
        id = id, uri = Uri.fromFile(f), name = f.name, size = tamanho, mimeType = "image/jpeg",
        type = MediaType.PHOTO, date = data, dateModified = data, bucketId = 1, bucketName = "Camera",
        relativePath = "DCIM/Camera/", width = 1, height = 1, durationMs = 0,
    )

    private fun scan(lista: List<Media>) = runBlocking { scanner.scan(lista) { _, _ -> } }

    @Test
    fun `copias identicas viram um grupo do mais antigo para o mais novo`() {
        val bytes = ByteArray(5000) { (it * 7).toByte() }
        val a = media(arquivo("a.jpg", bytes), 1, data = 300)
        val b = media(arquivo("b.jpg", bytes), 2, data = 100)
        val c = media(arquivo("c.jpg", bytes), 3, data = 200)
        val unico = media(arquivo("u.jpg", ByteArray(4000)), 4, data = 50)
        val grupos = scan(listOf(a, b, c, unico))
        assertEquals(1, grupos.size)
        assertEquals(listOf(2L, 3L, 1L), grupos[0].map { it.id })
    }

    @Test
    fun `mesmo tamanho com conteudo diferente nao e copia`() {
        val x = media(arquivo("x.jpg", ByteArray(3000) { 1 }), 1, 1)
        val y = media(arquivo("y.jpg", ByteArray(3000) { 2 }), 2, 2)
        assertTrue(scan(listOf(x, y)).isEmpty())
    }

    @Test
    fun `mesmo comeco e mesmo fim mas meio diferente nao e copia`() {
        // Arquivos grandes (> 128 KB) com os primeiros e os últimos 64 KB iguais passam pela etapa rápida;
        // só o SHA-256 completo separa. Sem isso, um vídeo editado no meio iria para a lixeira.
        val base = ByteArray(300_000) { (it % 251).toByte() }
        val outro = base.copyOf().also { it[150_000] = (it[150_000] + 1).toByte() }
        val p = media(arquivo("p.mp4", base), 1, 1)
        val q = media(arquivo("q.mp4", outro), 2, 2)
        assertTrue(scan(listOf(p, q)).isEmpty())
    }

    @Test
    fun `arquivo que nao abre fica de fora em vez de virar copia`() {
        val bytes = ByteArray(2000) { 9 }
        val ok1 = media(arquivo("ok1.jpg", bytes), 1, 1)
        val ok2 = media(arquivo("ok2.jpg", bytes), 2, 2)
        val sumiu = media(File(pasta.root, "nao-existe.jpg"), 3, 3, tamanho = 2000)
        val grupos = scan(listOf(ok1, ok2, sumiu))
        assertEquals(listOf(listOf(1L, 2L)), grupos.map { g -> g.map { it.id } })
    }

    @Test
    fun `grupos ordenados pelo espaco desperdicado`() {
        val pequeno = ByteArray(1000) { 3 }
        val grande = ByteArray(9000) { 4 }
        val lista = listOf(
            media(arquivo("p1.jpg", pequeno), 1, 1), media(arquivo("p2.jpg", pequeno), 2, 2),
            media(arquivo("g1.jpg", grande), 3, 3), media(arquivo("g2.jpg", grande), 4, 4),
        )
        val grupos = scan(lista)
        assertEquals(listOf(9000L, 1000L), grupos.map { it.first().size })
    }
}
