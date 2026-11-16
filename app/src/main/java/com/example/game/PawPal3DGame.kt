package com.example.game

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.graphics.OrthographicCamera
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import java.util.Calendar

/** Data exchanged between the Compose app and the native libGDX game. */
data class PetGameSession(
    val petName: String,
    val species: String,
    val furColorArgb: Int,
    var coins: Int,
    var hunger: Float,
    var happiness: Float,
    var energy: Float,
    var cleanliness: Float,
    var xp: Int = 0,
    var dailyCareCount: Int = 0,
    var dailyQuestClaimed: Boolean = false,
    var dailyCareDayKey: String = "",
    var lastUpdatedAtMillis: Long = System.currentTimeMillis()
) {
    val level: Int get() = (xp.coerceAtLeast(0) / 100) + 1
    val levelProgress: Float get() = (xp.coerceAtLeast(0) % 100) / 100f

    fun addXp(amount: Int): Boolean {
        val previousLevel = level
        xp = (xp + amount.coerceAtLeast(0)).coerceAtMost(1_000_000)
        return level > previousLevel
    }

    fun normalize() {
        coins = coins.coerceAtLeast(0)
        xp = xp.coerceIn(0, 1_000_000)
        dailyCareCount = dailyCareCount.coerceIn(0, 3)
        lastUpdatedAtMillis = lastUpdatedAtMillis.coerceAtLeast(0L)
        hunger = hunger.coerceIn(0f, 100f)
        happiness = happiness.coerceIn(0f, 100f)
        energy = energy.coerceIn(0f, 100f)
        cleanliness = cleanliness.coerceIn(0f, 100f)
    }
}

class PawPal3DGame(
    val session: PetGameSession,
    private val onExit: () -> Unit
) : Game() {
    override fun create() {
        session.normalize()
        setScreen(PawPal3DScreen(session, onExit))
    }

    override fun dispose() {
        super.dispose()
    }
}

private enum class PartMotion { BODY, HEAD, EYE, TAIL }
private data class PetPart(
    val instance: ModelInstance,
    val position: Vector3,
    val scale: Vector3,
    val motion: PartMotion = PartMotion.BODY
)

/** A true OpenGL 3D pet room. All scene meshes are generated locally, so no model download is needed. */
private class PawPal3DScreen(
    private val session: PetGameSession,
    private val onExit: () -> Unit
) : com.badlogic.gdx.ScreenAdapter() {
    private val uiWidth = 480f
    private val uiHeight = 800f
    private val uiCamera = OrthographicCamera()
    private val uiViewport = FitViewport(uiWidth, uiHeight, uiCamera)
    private val camera = PerspectiveCamera(48f, 1f, 1f)
    private val modelBatch = ModelBatch()
    private val spriteBatch = SpriteBatch()
    private val font = BitmapFont().apply { data.setScale(1.18f) }
    private val smallFont = BitmapFont().apply { data.setScale(0.88f) }
    private val layout = GlyphLayout()
    private val whitePixel: Texture
    private val environment = Environment()
    private val keyLight = DirectionalLight()
    private val fillLight = DirectionalLight()
    private val modelBuilder = ModelBuilder()
    private val modelCache = mutableMapOf<String, Model>()
    private val roomInstances = mutableListOf<ModelInstance>()
    private val petParts = mutableListOf<PetPart>()
    private val petCenter = Vector3(0f, 1.35f, 0f)
    private val intersection = Vector3()

    private var elapsed = 0f
    private var decayClock = 0f
    private var cameraAngle = 0f
    private var cameraDistance = 7.2f
    private var lastDragX = 0f
    private var draggingCamera = false
    private var miniGame = false
    private var miniGameTime = 20f
    private var miniGameScore = 0
    private var starX = 240f
    private var starY = 420f
    private var starMoveClock = 0f
    private var notice = "Sentuh aku, yuk!"
    private var noticeTime = 3f

    init {
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color.WHITE)
        pixmap.fill()
        whitePixel = Texture(pixmap)
        pixmap.dispose()
        whitePixel.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
        createEnvironment()
        createRoom()
        createPet()
        updateCamera()
    }

    private fun createEnvironment() {
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.58f, 0.58f, 0.64f, 1f))
        keyLight.set(0.85f, 0.78f, 0.66f, -0.6f, -1f, -0.35f)
        fillLight.set(0.25f, 0.32f, 0.45f, 0.55f, -0.45f, 0.6f)
        environment.add(keyLight)
        environment.add(fillLight)
    }

    /** Lighting follows the device's local clock to make the room feel alive. */
    private fun updateDayNightLighting(): Color {
        val clock = Calendar.getInstance()
        val hour = clock.get(Calendar.HOUR_OF_DAY) + clock.get(Calendar.MINUTE) / 60f
        val daylight = max(0f, sin(((hour - 6f) / 12f * Math.PI).toFloat()))
        val ambient = 0.28f + daylight * 0.43f
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, ambient, ambient * 0.98f, ambient * 1.04f, 1f))
        keyLight.set(
            0.18f + daylight * 0.78f,
            0.24f + daylight * 0.65f,
            0.38f + daylight * 0.40f,
            -0.6f, -1f, -0.35f
        )
        fillLight.set(0.12f + daylight * 0.24f, 0.16f + daylight * 0.27f, 0.27f + daylight * 0.24f, 0.55f, -0.45f, 0.6f)
        val nightSky = Color.valueOf("17243D")
        val daySky = Color.valueOf("A9D8F0")
        return Color(nightSky).lerp(daySky, daylight)
    }

    private fun dayPeriodLabel(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..10 -> "PAGI"
            in 11..14 -> "SIANG"
            in 15..17 -> "SORE"
            else -> "MALAM"
        }
    }

    private fun material(color: Color) = Material(ColorAttribute.createDiffuse(color))

    private fun cachedModel(key: String, create: () -> Model): Model =
        modelCache.getOrPut(key, create)

    private fun addBox(color: Color, position: Vector3, scale: Vector3) {
        val key = "box-${color.toIntBits()}"
        val model = cachedModel(key) {
            modelBuilder.createBox(
                1f, 1f, 1f, material(color),
                VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal
            )
        }
        val instance = ModelInstance(model)
        instance.transform.setToTranslation(position).scale(scale.x, scale.y, scale.z)
        roomInstances += instance
    }

    private fun addSphere(
        color: Color,
        position: Vector3,
        scale: Vector3,
        motion: PartMotion = PartMotion.BODY
    ) {
        val key = "sphere-${color.toIntBits()}"
        val model = cachedModel(key) {
            modelBuilder.createSphere(
                1f, 1f, 1f, 24, 20, material(color),
                VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal
            )
        }
        petParts += PetPart(ModelInstance(model), Vector3(position), Vector3(scale), motion)
    }

    private fun addCone(
        color: Color,
        position: Vector3,
        scale: Vector3,
        motion: PartMotion = PartMotion.HEAD
    ) {
        val key = "cone-${color.toIntBits()}"
        val model = cachedModel(key) {
            modelBuilder.createCone(
                1f, 1f, 1f, 20, material(color),
                VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal
            )
        }
        petParts += PetPart(ModelInstance(model), Vector3(position), Vector3(scale), motion)
    }

    private fun addTail(color: Color) {
        val key = "tail-${color.toIntBits()}"
        val model = cachedModel(key) {
            modelBuilder.createCylinder(
                0.34f, 1f, 0.34f, 16, material(color),
                VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal
            )
        }
        petParts += PetPart(
            ModelInstance(model), Vector3(0.20f, 1.03f, -0.57f),
            Vector3(0.16f, 0.55f, 0.16f), PartMotion.TAIL
        )
    }

    private fun createRoom() {
        val wall = Color(0.93f, 0.80f, 0.68f, 1f)
        val wallLight = Color(1f, 0.90f, 0.79f, 1f)
        val floor = Color(0.62f, 0.43f, 0.32f, 1f)
        val wood = Color(0.40f, 0.24f, 0.16f, 1f)
        val cream = Color(0.98f, 0.83f, 0.65f, 1f)
        val green = Color(0.22f, 0.52f, 0.32f, 1f)
        val rug = Color(0.91f, 0.65f, 0.48f, 1f)
        addBox(floor, Vector3(0f, -0.18f, 0f), Vector3(9f, 0.32f, 10f))
        addBox(wall, Vector3(0f, 2.05f, -4.2f), Vector3(9f, 4.3f, 0.18f))
        addBox(wallLight, Vector3(-4.35f, 2.05f, 0f), Vector3(0.18f, 4.3f, 8.5f))
        addBox(rug, Vector3(0f, 0.012f, 0.35f), Vector3(3.9f, 0.035f, 2.75f))

        // Sofa and cushions along the back wall.
        addBox(Color(0.48f, 0.42f, 0.72f, 1f), Vector3(0f, 0.42f, -2.75f), Vector3(3.25f, 0.72f, 0.86f))
        addBox(Color(0.57f, 0.50f, 0.79f, 1f), Vector3(0f, 1.02f, -3.12f), Vector3(3.25f, 0.82f, 0.25f))
        addBox(Color(0.96f, 0.72f, 0.78f, 1f), Vector3(-0.95f, 0.83f, -2.28f), Vector3(0.70f, 0.48f, 0.15f))
        addBox(Color(0.98f, 0.82f, 0.57f, 1f), Vector3(0.95f, 0.83f, -2.28f), Vector3(0.70f, 0.48f, 0.15f))

        // Window frame and glass.
        addBox(wood, Vector3(-2.25f, 2.45f, -4.05f), Vector3(1.75f, 1.55f, 0.08f))
        addBox(Color(0.51f, 0.78f, 0.91f, 1f), Vector3(-2.25f, 2.45f, -3.99f), Vector3(1.55f, 1.35f, 0.05f))
        addBox(Color(0.98f, 0.88f, 0.60f, 1f), Vector3(-2.25f, 2.45f, -3.94f), Vector3(0.05f, 1.4f, 0.04f))
        addBox(Color(0.98f, 0.88f, 0.60f, 1f), Vector3(-2.25f, 2.45f, -3.94f), Vector3(1.7f, 0.05f, 0.04f))

        // Coffee table, plant pot, and floor lamp.
        addBox(wood, Vector3(0f, 0.48f, 1.85f), Vector3(2.0f, 0.12f, 0.92f))
        addBox(wood, Vector3(-0.78f, 0.23f, 1.85f), Vector3(0.12f, 0.42f, 0.12f))
        addBox(wood, Vector3(0.78f, 0.23f, 1.85f), Vector3(0.12f, 0.42f, 0.12f))
        addBox(cream, Vector3(3.1f, 0.36f, -1.35f), Vector3(0.55f, 0.72f, 0.55f))
        addBox(green, Vector3(3.1f, 1.15f, -1.35f), Vector3(0.48f, 1.1f, 0.48f))
        addBox(wood, Vector3(3.15f, 1.05f, 2.0f), Vector3(0.10f, 2.0f, 0.10f))
        addBox(Color(1f, 0.82f, 0.48f, 1f), Vector3(3.15f, 2.05f, 2.0f), Vector3(0.72f, 0.38f, 0.65f))
    }

    private fun createPet() {
        val fur = colorFromArgb(session.furColorArgb)
        val cream = Color(0.98f, 0.88f, 0.72f, 1f)
        val innerEar = Color(0.89f, 0.47f, 0.48f, 1f)
        val dark = Color(0.11f, 0.13f, 0.16f, 1f)
        val white = Color.WHITE
        val collar = Color(0.96f, 0.69f, 0.22f, 1f)
        val species = session.species.uppercase()

        addSphere(fur, Vector3(0f, 0.98f, 0f), Vector3(0.88f, 0.98f, 0.68f))
        addSphere(cream, Vector3(0f, 0.88f, 0.48f), Vector3(0.52f, 0.58f, 0.19f))
        addSphere(fur, Vector3(0f, 1.83f, 0.08f), Vector3(0.90f, 0.80f, 0.76f), PartMotion.HEAD)

        // Ears are selected from the pet species instead of using a flat sprite.
        when (species) {
            "RABBIT" -> {
                addSphere(fur, Vector3(-0.34f, 2.48f, 0.02f), Vector3(0.22f, 0.68f, 0.20f), PartMotion.HEAD)
                addSphere(fur, Vector3(0.34f, 2.48f, 0.02f), Vector3(0.22f, 0.68f, 0.20f), PartMotion.HEAD)
                addSphere(innerEar, Vector3(-0.34f, 2.49f, 0.19f), Vector3(0.10f, 0.48f, 0.06f), PartMotion.HEAD)
                addSphere(innerEar, Vector3(0.34f, 2.49f, 0.19f), Vector3(0.10f, 0.48f, 0.06f), PartMotion.HEAD)
            }
            "DOG" -> {
                addSphere(fur, Vector3(-0.58f, 2.08f, 0.00f), Vector3(0.25f, 0.48f, 0.20f), PartMotion.HEAD)
                addSphere(fur, Vector3(0.58f, 2.08f, 0.00f), Vector3(0.25f, 0.48f, 0.20f), PartMotion.HEAD)
                addSphere(innerEar, Vector3(-0.59f, 2.08f, 0.16f), Vector3(0.13f, 0.28f, 0.05f), PartMotion.HEAD)
                addSphere(innerEar, Vector3(0.59f, 2.08f, 0.16f), Vector3(0.13f, 0.28f, 0.05f), PartMotion.HEAD)
            }
            "BEAR", "CAPYBARA" -> {
                addSphere(fur, Vector3(-0.47f, 2.34f, 0.00f), Vector3(0.27f, 0.29f, 0.22f), PartMotion.HEAD)
                addSphere(fur, Vector3(0.47f, 2.34f, 0.00f), Vector3(0.27f, 0.29f, 0.22f), PartMotion.HEAD)
                addSphere(innerEar, Vector3(-0.47f, 2.35f, 0.17f), Vector3(0.13f, 0.15f, 0.06f), PartMotion.HEAD)
                addSphere(innerEar, Vector3(0.47f, 2.35f, 0.17f), Vector3(0.13f, 0.15f, 0.06f), PartMotion.HEAD)
            }
            else -> {
                addCone(fur, Vector3(-0.40f, 2.37f, 0.00f), Vector3(0.43f, 0.70f, 0.38f))
                addCone(fur, Vector3(0.40f, 2.37f, 0.00f), Vector3(0.43f, 0.70f, 0.38f))
                addSphere(innerEar, Vector3(-0.40f, 2.34f, 0.20f), Vector3(0.16f, 0.30f, 0.06f), PartMotion.HEAD)
                addSphere(innerEar, Vector3(0.40f, 2.34f, 0.20f), Vector3(0.16f, 0.30f, 0.06f), PartMotion.HEAD)
            }
        }

        // Eyes, muzzle, nose, and simple smiling mouth.
        for (side in listOf(-1f, 1f)) {
            addSphere(white, Vector3(side * 0.25f, 1.92f, 0.72f), Vector3(0.17f, 0.20f, 0.08f), PartMotion.EYE)
            addSphere(dark, Vector3(side * 0.25f, 1.91f, 0.80f), Vector3(0.075f, 0.095f, 0.04f), PartMotion.EYE)
            addSphere(white, Vector3(side * 0.27f, 1.96f, 0.84f), Vector3(0.028f, 0.032f, 0.018f), PartMotion.EYE)
            addSphere(fur, Vector3(side * 0.19f, 1.57f, 0.70f), Vector3(0.25f, 0.19f, 0.20f), PartMotion.HEAD)
        }
        addSphere(Color(0.15f, 0.10f, 0.13f, 1f), Vector3(0f, 1.66f, 0.88f), Vector3(0.13f, 0.10f, 0.07f), PartMotion.HEAD)
        addSphere(dark, Vector3(0f, 1.48f, 0.82f), Vector3(0.12f, 0.035f, 0.045f), PartMotion.HEAD)

        // Arms and feet.
        addSphere(fur, Vector3(-0.57f, 1.07f, 0.03f), Vector3(0.24f, 0.52f, 0.24f))
        addSphere(fur, Vector3(0.57f, 1.07f, 0.03f), Vector3(0.24f, 0.52f, 0.24f))
        addSphere(fur, Vector3(-0.32f, 0.22f, 0.10f), Vector3(0.31f, 0.24f, 0.39f))
        addSphere(fur, Vector3(0.32f, 0.22f, 0.10f), Vector3(0.31f, 0.24f, 0.39f))
        addSphere(collar, Vector3(0f, 1.34f, 0.42f), Vector3(0.57f, 0.12f, 0.20f))
        addSphere(Color(0.99f, 0.86f, 0.42f, 1f), Vector3(0f, 1.25f, 0.58f), Vector3(0.11f, 0.13f, 0.08f))
        addTail(fur)
    }

    private fun colorFromArgb(argb: Int): Color = Color(
        ((argb shr 16) and 0xFF) / 255f,
        ((argb shr 8) and 0xFF) / 255f,
        (argb and 0xFF) / 255f,
        ((argb ushr 24) and 0xFF) / 255f
    )

    private fun updateCamera() {
        val x = sin(cameraAngle) * cameraDistance
        val z = cos(cameraAngle) * cameraDistance
        camera.position.set(x, 3.15f, z)
        camera.lookAt(0f, 1.25f, 0f)
        camera.up.set(Vector3.Y)
        camera.near = 0.1f
        camera.far = 80f
        camera.fieldOfView = 47f
        camera.viewportWidth = Gdx.graphics.width.toFloat().coerceAtLeast(1f)
        camera.viewportHeight = Gdx.graphics.height.toFloat().coerceAtLeast(1f)
        camera.update()
    }

    private fun updatePetAnimation() {
        val bob = sin(elapsed * 2.4f) * 0.035f
        val tailRotation = sin(elapsed * 5.2f) * 24f
        val headRotation = sin(elapsed * 1.25f) * 3.5f
        val blinkPhase = (elapsed % 4.8f - 4.52f) / 0.28f
        val blinkAmount = if (blinkPhase in 0f..1f) sin(blinkPhase * MathUtils.PI) else 0f
        for (part in petParts) {
            val y = part.position.y + bob
            val transform = part.instance.transform.idt()
            when (part.motion) {
                PartMotion.HEAD, PartMotion.EYE -> {
                    // Rotate facial features and ears together around the head's shared pivot.
                    transform.translate(0f, 1.83f, 0.08f)
                        .rotate(Vector3.Y, headRotation)
                        .translate(0f, -1.83f, -0.08f)
                        .translate(part.position.x, y, part.position.z)
                }
                PartMotion.TAIL -> {
                    transform.translate(part.position.x, y, part.position.z)
                        .rotate(Vector3.X, 90f)
                        .rotate(Vector3.Y, tailRotation)
                }
                PartMotion.BODY -> transform.translate(part.position.x, y, part.position.z)
            }
            val verticalScale = if (part.motion == PartMotion.EYE) {
                part.scale.y * (1f - 0.94f * blinkAmount)
            } else part.scale.y
            transform.scale(part.scale.x, verticalScale, part.scale.z)
        }
    }

    override fun show() {
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun keyDown(keycode: Int): Boolean {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                    leaveGame()
                    return true
                }
                return false
            }

            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                lastDragX = screenX.toFloat()
                draggingCamera = false
                val point = uiViewport.unproject(Vector2(screenX.toFloat(), screenY.toFloat()))
                if (point.y > 720f && point.x < 115f) {
                    leaveGame()
                    return true
                }
                if (miniGame) {
                    if (point.y < 145f) {
                        finishMiniGame()
                    } else if (point.dst(starX, starY) < 48f) {
                        miniGameScore++
                        session.coins += 1
                        val leveledUp = session.addXp(2)
                        starX = MathUtils.random(65f, 415f)
                        starY = MathUtils.random(235f, 585f)
                        starMoveClock = 0f
                        showNotice(if (leveledUp) "Naik level! ${session.petName} makin hebat! +1 koin ⭐" else "+1 koin! Cepat tangkap lagi!")
                    }
                    return true
                }
                if (point.y in 42f..132f) {
                    when {
                        point.x < 120f -> care("Makan", 5, 22f, 0f, 0f, 0f)
                        point.x < 240f -> care("Main", 4, 0f, 20f, -8f, 0f)
                        point.x < 360f -> care("Mandi", 3, 0f, 2f, 0f, 32f)
                        else -> care("Tidur", 0, -5f, 4f, 30f, 0f)
                    }
                    return true
                }
                if (point.y in 150f..215f && point.x in 75f..405f) {
                    startMiniGame()
                    return true
                }
                if (point.y in 250f..600f) {
                    val ray = camera.getPickRay(screenX.toFloat(), screenY.toFloat())
                    if (Intersector.intersectRaySphere(ray, petCenter, 1.35f, intersection)) {
                        session.happiness = (session.happiness + 3f).coerceAtMost(100f)
                        showNotice("${session.petName}: Hehe, geli! Makasih ya!")
                    } else {
                        draggingCamera = true
                    }
                    return true
                }
                return true
            }

            override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
                val point = uiViewport.unproject(Vector2(screenX.toFloat(), screenY.toFloat()))
                if (point.y in 250f..650f || draggingCamera) {
                    val dx = screenX - lastDragX
                    cameraAngle = (cameraAngle + dx * 0.006f) % (MathUtils.PI2)
                    lastDragX = screenX.toFloat()
                    updateCamera()
                    return true
                }
                return false
            }

            override fun scrolled(amountX: Float, amountY: Float): Boolean {
                cameraDistance = (cameraDistance + amountY * 0.35f).coerceIn(5.3f, 9.5f)
                updateCamera()
                return true
            }
        }
    }

    private fun leaveGame() {
        saveProgress()
        Gdx.app.postRunnable { onExit.invoke() }
    }

    private fun care(action: String, cost: Int, hunger: Float, happiness: Float, energy: Float, clean: Float) {
        if (cost > session.coins) {
            showNotice("Koin kurang. Main tangkap bintang dulu!")
            return
        }
        val hasBenefit = when (action) {
            "Makan" -> session.hunger < 99f
            "Main" -> session.happiness < 99f && session.energy > 8f
            "Mandi" -> session.cleanliness < 99f
            else -> session.energy < 99f
        }
        if (!hasBenefit) {
            showNotice("${session.petName} sudah cukup ${action.lowercase()}!")
            return
        }
        session.coins -= cost
        session.hunger = (session.hunger + hunger).coerceIn(0f, 100f)
        session.happiness = (session.happiness + happiness).coerceIn(0f, 100f)
        session.energy = (session.energy + energy).coerceIn(0f, 100f)
        session.cleanliness = (session.cleanliness + clean).coerceIn(0f, 100f)
        val leveledUp = session.addXp(12)
        var dailyQuestCompleted = false
        if (!session.dailyQuestClaimed && session.dailyCareCount < 3) {
            session.dailyCareCount++
            if (session.dailyCareCount >= 3) {
                session.dailyQuestClaimed = true
                session.coins += 25
                dailyQuestCompleted = true
            }
        }
        showNotice(
            when {
                dailyQuestCompleted -> "Misi harian selesai! +25 koin 🎁"
                leveledUp -> "Naik level! ${session.petName} mencapai Level ${session.level}! ⭐"
                else -> when (action) {
            "Makan" -> "Nyam! ${session.petName} kenyang."
            "Main" -> "Asyik! ${session.petName} senang bermain."
                    "Mandi" -> "Segar dan bersih!"
                    else -> "Zzz... ${session.petName} pulih tenaga."
                }
            }
        )
        saveProgress()
    }

    private fun startMiniGame() {
        miniGame = true
        miniGameTime = 20f
        miniGameScore = 0
        starMoveClock = 0f
        starX = MathUtils.random(65f, 415f)
        starY = MathUtils.random(235f, 585f)
        showNotice("Tangkap bintangnya sebelum waktu habis!")
    }

    private fun finishMiniGame() {
        miniGame = false
        val leveledUp = session.addXp(5 + miniGameScore)
        showNotice(
            if (leveledUp) "Naik level! Bonus $miniGameScore koin dan XP ⭐"
            else "Selesai! Skor $miniGameScore, dapat $miniGameScore koin."
        )
        saveProgress()
    }

    private fun showNotice(text: String) {
        notice = text
        noticeTime = 3.2f
    }

    override fun render(delta: Float) {
        val dt = delta.coerceIn(0f, 0.05f)
        elapsed += dt
        if (noticeTime > 0f) noticeTime -= dt
        decayClock += dt
        if (!miniGame && decayClock >= 18f) {
            decayClock = 0f
            session.hunger = (session.hunger - 1.2f).coerceAtLeast(0f)
            session.energy = (session.energy - 0.5f).coerceAtLeast(0f)
            session.happiness = (session.happiness - 0.4f).coerceAtLeast(0f)
        }
        if (miniGame) {
            miniGameTime -= dt
            starMoveClock += dt
            if (starMoveClock >= 1.0f) {
                starMoveClock = 0f
                starX = MathUtils.random(65f, 415f)
                starY = MathUtils.random(235f, 585f)
            }
            if (miniGameTime <= 0f) finishMiniGame()
        }

        val sky = updateDayNightLighting()
        ScreenUtils.clear(sky.r, sky.g, sky.b, 1f)
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        updateCamera()
        updatePetAnimation()
        modelBatch.begin(camera)
        roomInstances.forEach { modelBatch.render(it, environment) }
        petParts.forEach { modelBatch.render(it.instance, environment) }
        modelBatch.end()
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        drawHud()
    }

    private fun drawHud() {
        uiViewport.apply()
        spriteBatch.projectionMatrix = uiCamera.combined
        spriteBatch.begin()
        drawPanel(16f, 724f, 448f, 62f, Color(0.98f, 0.96f, 0.92f, 0.93f))
        font.color = Color.valueOf("26364A")
        font.draw(spriteBatch, "PAWPAL 3D", 150f, 766f)
        smallFont.color = Color.valueOf("576B7D")
        smallFont.draw(spriteBatch, "• ${dayPeriodLabel()}", 365f, 766f)
        smallFont.draw(spriteBatch, "< Kembali", 30f, 752f)
        smallFont.draw(spriteBatch, "${session.petName}  •  Lv.${session.level}  •  ${session.coins} koin", 151f, 741f)
        drawPanel(151f, 729f, 250f, 5f, Color.valueOf("DDE3E8"))
        drawPanel(151f, 729f, 250f * session.levelProgress, 5f, Color.valueOf("F2B84B"))
        smallFont.color = Color.valueOf("64748B")
        smallFont.draw(spriteBatch, "${session.xp % 100}/100 XP", 407f, 735f)
        drawPanel(20f, 590f, 440f, 118f, Color(0.98f, 0.97f, 0.94f, 0.89f))
        drawStat("Lapar", session.hunger, 681f, Color.valueOf("F2B544"))
        drawStat("Senang", session.happiness, 654f, Color.valueOf("EF8DA9"))
        drawStat("Energi", session.energy, 627f, Color.valueOf("77B9E8"))
        drawStat("Bersih", session.cleanliness, 600f, Color.valueOf("6AC3A7"))

        drawPanel(42f, 218f, 396f, 42f, Color(0.98f, 0.97f, 0.94f, 0.90f))
        smallFont.color = Color.valueOf("36485B")
        centerText(smallFont, if (noticeTime > 0f) notice else "Geser layar untuk memutar kamera", 243f)

        if (miniGame) {
            drawPanel(16f, 724f, 448f, 62f, Color(0.99f, 0.94f, 0.94f, 0.96f))
            font.color = Color.valueOf("9B355D")
            centerText(font, "TANGKAP BINTANG", 766f)
            smallFont.color = Color.valueOf("6C5260")
            centerText(smallFont, "Skor $miniGameScore    Waktu ${max(0, miniGameTime.toInt())} dtk", 739f)
            font.color = Color.valueOf("F5AE35")
            centerText(font, "*", starY + 15f, starX)
            drawButton(92f, 45f, 296f, 82f, "KEMBALI KE RUANGAN", Color.valueOf("FCE0E7"))
        } else {
            drawButton(92f, 158f, 296f, 52f, "MAIN TANGKAP BINTANG", Color.valueOf("FFE7AC"))
            drawButton(10f, 42f, 106f, 88f, "MAKAN", Color.valueOf("FFE1B8"))
            drawButton(126f, 42f, 106f, 88f, "MAIN", Color.valueOf("E5DFFD"))
            drawButton(242f, 42f, 106f, 88f, "MANDI", Color.valueOf("D6F1EB"))
            drawButton(358f, 42f, 112f, 88f, "TIDUR", Color.valueOf("D9E8F8"))
            drawPanel(42f, 271f, 396f, 27f, Color(0.98f, 0.96f, 0.90f, 0.88f))
            smallFont.color = Color.valueOf("775A2C")
            centerText(smallFont, if (session.dailyQuestClaimed) "Misi harian selesai • Hadiah sudah diklaim!" else "Misi harian: Rawat hewan 3x • ${session.dailyCareCount}/3 • Hadiah 25 koin", 288f)
        }
        spriteBatch.end()
    }

    private fun drawStat(label: String, value: Float, y: Float, color: Color) {
        smallFont.color = Color.valueOf("3B4A5B")
        smallFont.draw(spriteBatch, label, 36f, y + 12f)
        drawPanel(124f, y, 278f, 10f, Color.valueOf("DFE5E8"))
        drawPanel(124f, y, 278f * (value / 100f), 10f, color)
        smallFont.color = Color.valueOf("3B4A5B")
        smallFont.draw(spriteBatch, "${value.toInt()}%", 410f, y + 12f)
    }

    private fun drawButton(x: Float, y: Float, w: Float, h: Float, label: String, color: Color) {
        drawPanel(x, y, w, h, color)
        font.color = Color.valueOf("29394A")
        centerText(font, label, y + h * 0.57f, x + w / 2f)
    }

    private fun drawPanel(x: Float, y: Float, w: Float, h: Float, color: Color) {
        spriteBatch.setColor(color)
        spriteBatch.draw(whitePixel, x, y, w, h)
        spriteBatch.setColor(Color.WHITE)
    }

    private fun centerText(textFont: BitmapFont, text: String, y: Float, centerX: Float = 240f) {
        layout.setText(textFont, text)
        textFont.draw(spriteBatch, layout, centerX - layout.width / 2f, y)
    }

    override fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        uiViewport.update(width, height, true)
        updateCamera()
    }

    override fun pause() {
        saveProgress()
    }

    private fun saveProgress() {
        if (Gdx.app == null) return
        val prefs = Gdx.app.getPreferences("pawpal_3d_progress")
        session.lastUpdatedAtMillis = System.currentTimeMillis()
        prefs.putInteger("coins", session.coins)
            .putFloat("hunger", session.hunger)
            .putFloat("happiness", session.happiness)
            .putFloat("energy", session.energy)
            .putFloat("cleanliness", session.cleanliness)
            .putInteger("xp", session.xp)
            .putInteger("daily_care_count", session.dailyCareCount)
            .putBoolean("daily_quest_claimed", session.dailyQuestClaimed)
            .putString("daily_care_day", session.dailyCareDayKey)
            .putLong("last_updated_at", session.lastUpdatedAtMillis)
            .flush()
    }

    override fun hide() {
        saveProgress()
    }

    override fun dispose() {
        saveProgress()
        modelBatch.dispose()
        spriteBatch.dispose()
        font.dispose()
        smallFont.dispose()
        whitePixel.dispose()
        modelCache.values.forEach { it.dispose() }
        modelCache.clear()
        roomInstances.clear()
        petParts.clear()
    }
}
