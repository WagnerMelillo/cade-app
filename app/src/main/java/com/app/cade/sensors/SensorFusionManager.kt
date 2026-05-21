package com.app.cade.sensors

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fusão dos sensores de georreferenciamento do próprio aparelho:
 *  - Bússola (heading/azimute) via TYPE_ROTATION_VECTOR (já funde acelerômetro,
 *    magnetômetro e giroscópio internamente);
 *  - Inclinômetro (pitch/roll) para saber como o celular está sendo segurado;
 *  - Correção de declinação magnética -> NORTE VERDADEIRO, quando há localização.
 *
 * IMPORTANTE: esta classe não localiza ninguém. Ela fornece a ORIENTAÇÃO do SEU
 * aparelho. Para a seta apontar para a pessoa, combinamos este heading com a
 * POSIÇÃO do alvo (vinda de GPS/UWB) -> ângulo da seta = bearingAteAlvo - heading.
 */
class SensorFusionManager(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    /** Heading bruto em relação ao norte MAGNÉTICO (0..360, 0 = norte). */
    private val _magneticHeading = MutableStateFlow(0f)
    val magneticHeading: StateFlow<Float> = _magneticHeading

    /** Heading corrigido para o NORTE VERDADEIRO (0..360). É o usado pela seta. */
    private val _trueHeading = MutableStateFlow(0f)
    val trueHeading: StateFlow<Float> = _trueHeading

    /** Inclinação frente/trás (graus). */
    private val _pitch = MutableStateFlow(0f)
    val pitch: StateFlow<Float> = _pitch

    /** Inclinação lateral (graus). */
    private val _roll = MutableStateFlow(0f)
    val roll: StateFlow<Float> = _roll

    /** Confiabilidade da bússola (0 = ruim … 3 = alta). Útil para avisar "calibre". */
    private val _accuracy = MutableStateFlow(SensorManager.SENSOR_STATUS_UNRELIABLE)
    val accuracy: StateFlow<Int> = _accuracy

    val isAvailable: Boolean get() = rotationSensor != null

    private var declinationDegrees: Float = 0f

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Filtro passa-baixa circular (média de seno/cosseno) para a agulha não tremer
    // nem "saltar" ao cruzar 0°/360°.
    private var smoothedSin = 0f
    private var smoothedCos = 0f
    private val alpha = 0.12f

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /** Recalcula a declinação magnética para converter o heading em norte verdadeiro. */
    fun updateDeclination(location: Location?) {
        if (location != null) {
            val gmf = GeomagneticField(
                location.latitude.toFloat(),
                location.longitude.toFloat(),
                location.altitude.toFloat(),
                System.currentTimeMillis()
            )
            declinationDegrees = gmf.declination
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            val azimuthRad = orientationAngles[0]
            smoothedSin += alpha * (sin(azimuthRad) - smoothedSin)
            smoothedCos += alpha * (cos(azimuthRad) - smoothedCos)
            val smoothAzimuthDeg =
                (Math.toDegrees(atan2(smoothedSin, smoothedCos).toDouble()).toFloat() + 360f) % 360f

            _magneticHeading.value = smoothAzimuthDeg
            _trueHeading.value = (smoothAzimuthDeg + declinationDegrees + 360f) % 360f
            _pitch.value = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
            _roll.value = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            _accuracy.value = accuracy
        }
    }
}
