package raven.sample.viewportopengl

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.value
import kotlinx.cinterop.cValuesOf
import platform.OpenGL3.GL_ARRAY_BUFFER
import platform.OpenGL3.GL_COLOR_BUFFER_BIT
import platform.OpenGL3.GL_COMPILE_STATUS
import platform.OpenGL3.GL_DEPTH_BUFFER_BIT
import platform.OpenGL3.GL_DEPTH_TEST
import platform.OpenGL3.GL_FALSE
import platform.OpenGL3.GL_FLOAT
import platform.OpenGL3.GL_FRAGMENT_SHADER
import platform.OpenGL3.GL_LINES
import platform.OpenGL3.GL_LINK_STATUS
import platform.OpenGL3.GL_RENDERER
import platform.OpenGL3.GL_STATIC_DRAW
import platform.OpenGL3.GL_TRIANGLES
import platform.OpenGL3.GL_VERTEX_SHADER
import platform.OpenGL3.glAttachShader
import platform.OpenGL3.glBindAttribLocation
import platform.OpenGL3.glBindBuffer
import platform.OpenGL3.glBindVertexArray
import platform.OpenGL3.glBufferData
import platform.OpenGL3.glClear
import platform.OpenGL3.glClearColor
import platform.OpenGL3.glCompileShader
import platform.OpenGL3.glCreateProgram
import platform.OpenGL3.glCreateShader
import platform.OpenGL3.glDeleteBuffers
import platform.OpenGL3.glDeleteProgram
import platform.OpenGL3.glDeleteShader
import platform.OpenGL3.glDeleteVertexArrays
import platform.OpenGL3.glDrawArrays
import platform.OpenGL3.glEnable
import platform.OpenGL3.glEnableVertexAttribArray
import platform.OpenGL3.glGenBuffers
import platform.OpenGL3.glGenVertexArrays
import platform.OpenGL3.glGetProgramInfoLog
import platform.OpenGL3.glGetProgramiv
import platform.OpenGL3.glGetShaderInfoLog
import platform.OpenGL3.glGetShaderiv
import platform.OpenGL3.glGetString
import platform.OpenGL3.glGetUniformLocation
import platform.OpenGL3.glLinkProgram
import platform.OpenGL3.glShaderSource
import platform.OpenGL3.glUniformMatrix4fv
import platform.OpenGL3.glUseProgram
import platform.OpenGL3.glVertexAttribPointer
import platform.OpenGL3.glViewport

private const val VERTEX_SHADER = """#version 150 core
in vec3 position;
in vec3 color;
uniform mat4 transform;
out vec3 shade;
void main() {
    shade = color;
    gl_Position = transform * vec4(position, 1.0);
}
"""

private const val FRAGMENT_SHADER = """#version 150 core
in vec3 shade;
out vec4 fragColor;
void main() {
    fragColor = vec4(shade, 1.0);
}
"""

actual fun glScene(): Scene? = CoreGlScene()

/** The cube with a vertex and a fragment shader over one vertex buffer, in a 3.2 core context. */
private class CoreGlScene : Scene {
    private var program = 0u
    private var vertexArray = 0u
    private var buffer = 0u
    private var transform = -1

    override fun init(): String {
        program = glCreateProgram()
        val vertex = compile(GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragment = compile(GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        glAttachShader(program, vertex)
        glAttachShader(program, fragment)
        glBindAttribLocation(program, 0u, "position")
        glBindAttribLocation(program, 1u, "color")
        glLinkProgram(program)
        memScoped {
            val status = alloc<IntVar>()
            glGetProgramiv(program, GL_LINK_STATUS.toUInt(), status.ptr)
            check(status.value != GL_FALSE) { "the cube's program doesn't link: ${log { size, text -> glGetProgramInfoLog(program, size, null, text) }}" }
        }
        glDeleteShader(vertex)
        glDeleteShader(fragment)
        transform = glGetUniformLocation(program, "transform")

        memScoped {
            val id = alloc<UIntVar>()
            glGenVertexArrays(1, id.ptr)
            vertexArray = id.value
            glGenBuffers(1, id.ptr)
            buffer = id.value
        }
        glBindVertexArray(vertexArray)
        glBindBuffer(GL_ARRAY_BUFFER.toUInt(), buffer)
        cubeVertices().usePinned { glBufferData(GL_ARRAY_BUFFER.toUInt(), (it.get().size * 4).toLong(), it.addressOf(0), GL_STATIC_DRAW.toUInt()) }
        val stride = 6 * 4
        glVertexAttribPointer(0u, 3, GL_FLOAT.toUInt(), GL_FALSE.toUByte(), stride, null)
        glEnableVertexAttribArray(0u)
        glVertexAttribPointer(1u, 3, GL_FLOAT.toUInt(), GL_FALSE.toUByte(), stride, (3L * 4).toCPointer<ByteVar>())
        glEnableVertexAttribArray(1u)
        glBindVertexArray(0u)

        return glGetString(GL_RENDERER.toUInt())?.reinterpret<ByteVar>()?.toKString() ?: "OpenGL"
    }

    override fun draw(width: Int, height: Int, angle: Float, wireframe: Boolean) {
        glViewport(0, 0, width, height)
        glClearColor(clearColor[0], clearColor[1], clearColor[2], 1f)
        glClear((GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT).toUInt())
        glEnable(GL_DEPTH_TEST.toUInt())
        glUseProgram(program)
        cubeTransform(width, height, angle).usePinned { glUniformMatrix4fv(transform, 1, GL_FALSE.toUByte(), it.addressOf(0)) }
        glBindVertexArray(vertexArray)
        if (wireframe) {
            glDrawArrays(GL_LINES.toUInt(), CUBE_TRIANGLE_VERTICES, CUBE_EDGE_VERTICES)
        } else {
            glDrawArrays(GL_TRIANGLES.toUInt(), 0, CUBE_TRIANGLE_VERTICES)
        }
        glBindVertexArray(0u)
    }

    override fun dispose() {
        memScoped {
            glDeleteBuffers(1, cValuesOf(buffer))
            glDeleteVertexArrays(1, cValuesOf(vertexArray))
        }
        glDeleteProgram(program)
    }

    private fun compile(type: Int, source: String): UInt {
        val shader = glCreateShader(type.toUInt())
        memScoped {
            glShaderSource(shader, 1, cValuesOf(source.cstr.ptr), null)
            glCompileShader(shader)
            val status = alloc<IntVar>()
            glGetShaderiv(shader, GL_COMPILE_STATUS.toUInt(), status.ptr)
            check(status.value != GL_FALSE) { "a shader of the cube doesn't compile: ${log { size, text -> glGetShaderInfoLog(shader, size, null, text) }}" }
        }
        return shader
    }

    private fun log(read: (Int, CPointer<ByteVar>) -> Unit): String = memScoped {
        val text = allocArray<ByteVar>(1024)
        read(1024, text)
        text.toKString()
    }
}
