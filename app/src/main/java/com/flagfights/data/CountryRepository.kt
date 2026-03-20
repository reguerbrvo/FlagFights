package com.flagfights.data

import android.content.Context
import com.flagfights.domain.CountryFlag
import org.json.JSONArray

class CountryRepository(
    private val countries: List<CountryFlag>
) {
    init {
        require(countries.distinctBy { it.isoCode }.size >= ROUND_OPTIONS_COUNT) {
            "At least $ROUND_OPTIONS_COUNT unique countries are required."
        }
    }

    fun getAllCountries(): List<CountryFlag> = countries

    fun getRoundCandidates(recentCountryCodes: List<String> = emptyList(), recentWindow: Int = 2): CountryQuestion {
        val recentCodes = recentCountryCodes.takeLast(recentWindow).toSet()
        val availableTargets = countries.filterNot { it.isoCode in recentCodes }
        val targetPool = if (availableTargets.isNotEmpty()) availableTargets else countries
        val targetCountry = targetPool.random()
        val distractors = countries
            .asSequence()
            .filterNot { it.isoCode == targetCountry.isoCode }
            .shuffled()
            .take(ROUND_OPTIONS_COUNT - 1)
            .toList()

        val options = (distractors + targetCountry).shuffled()

        return CountryQuestion(
            targetCountry = targetCountry,
            options = options,
            correctAnswer = targetCountry
        )
    }

    data class CountryQuestion(
        val targetCountry: CountryFlag,
        val options: List<CountryFlag>,
        val correctAnswer: CountryFlag
    )

    companion object {
        private const val ROUND_OPTIONS_COUNT = 4

        fun fromAsset(context: Context, assetName: String = "countries.json"): CountryRepository {
            val json = context.assets.open(assetName).bufferedReader().use { it.readText() }
            return fromJson(json)
        }

        fun fromJson(json: String): CountryRepository {
            val parsedCountries = JSONArray(json).let { jsonArray ->
                List(jsonArray.length()) { index ->
                    val item = jsonArray.getJSONObject(index)
                    CountryFlag(
                        countryName = item.getString("name"),
                        isoCode = item.getString("isoCode"),
                        flagEmoji = item.getString("flagEmoji")
                    )
                }
            }

            return CountryRepository(parsedCountries.distinctBy { it.isoCode })
        }
    }
}
