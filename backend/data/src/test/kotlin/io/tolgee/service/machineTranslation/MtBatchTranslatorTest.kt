package io.tolgee.service.machineTranslation

import io.tolgee.component.machineTranslation.MtServiceManager
import io.tolgee.component.machineTranslation.TranslateResult
import io.tolgee.component.machineTranslation.metadata.ExampleItem
import io.tolgee.component.machineTranslation.providers.GoogleTranslationProvider
import io.tolgee.component.machineTranslation.providers.LlmTranslationProvider
import io.tolgee.constants.MtServiceType
import io.tolgee.dtos.cacheable.LanguageDto
import io.tolgee.dtos.cacheable.ProjectDto
import io.tolgee.model.mtServiceConfig.Formality
import io.tolgee.model.views.TranslationMemoryItemView
import io.tolgee.service.bigMeta.BigMetaService
import io.tolgee.service.language.LanguageService
import io.tolgee.service.project.ProjectService
import io.tolgee.service.translation.TranslationMemoryService
import io.tolgee.testing.assert
import jakarta.persistence.EntityManager
import jakarta.persistence.TypedQuery
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.notNull
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationContext
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import kotlin.reflect.KClass

class MtBatchTranslatorTest {
  private lateinit var preparedKey: KeyForMt
  private lateinit var mtServiceManagerResults: List<TranslateResult>

  @Test
  fun `correctly translates plurals for service not supporting plurals`() {
    prepareValidKey()
    prepareSeparatePluralsResponse()

    val context = getMtTranslatorContext()
    val translator = MtBatchTranslator(context)

    val translated =
      translator.translate(
        listOf(
          MtBatchItemParams(
            keyId = 1,
            baseTranslationText = preparedKey.baseTranslation,
            targetLanguageId = 1,
            service = MtServiceType.GOOGLE,
          ),
        ),
      ).first()

    translated.translatedText.assert.isEqualTo(
      "{value, plural,\n" +
        "one {Jeden pes}\n" +
        "few {'#' psi}\n" +
        "many {'#' psa}\n" +
        "other {'#' psů}\n" +
        "}",
    )
    translated.actualPrice.assert.isEqualTo(400)
  }

  @Test
  fun `correctly translates plurals for service supporting plurals`() {
    prepareValidKey()
    prepareSingleValuePluralResponse()

    val context = getMtTranslatorContext()
    val translator = MtBatchTranslator(context)

    val translated =
      translator.translate(
        listOf(
          MtBatchItemParams(
            keyId = 1,
            baseTranslationText = preparedKey.baseTranslation,
            targetLanguageId = 1,
            service = MtServiceType.PROMPT,
          ),
        ),
      ).first()

    translated.translatedText.assert.isEqualTo(
      "{value, plural,\n" +
        "one {Jeden pes}\n" +
        "few {'#' psi}\n" +
        "many {'#' psa}\n" +
        "other {'#' psů}\n" +
        "}",
    )
    translated.actualPrice.assert.isEqualTo(100)
  }

  private fun prepareValidKey() {
    preparedKey =
      KeyForMt(
        id = 1,
        name = "key",
        namespace = "test",
        description = "test",
        baseTranslation = "{value, plural, one {# dog} other {# dogs}}",
        isPlural = true,
      )
  }

  private fun prepareSingleValuePluralResponse() {
    mtServiceManagerResults =
      listOf(
        TranslateResult(
          translatedText = null,
          translatedPluralForms =
            mapOf(
              "one" to "Jeden pes",
              "few" to "# psi",
              "many" to "# psa",
              "other" to "# psů",
            ),
          actualPrice = 100,
          usedService = MtServiceType.PROMPT,
        ),
      )
  }

  private fun prepareSeparatePluralsResponse() {
    mtServiceManagerResults =
      listOf(
        TranslateResult(
          translatedText = "Jeden pes",
          actualPrice = 100,
          usedService = MtServiceType.GOOGLE,
        ),
        TranslateResult(
          translatedText = "<x id=\"tolgee-number\">2</x> psi",
          actualPrice = 100,
          usedService = MtServiceType.GOOGLE,
        ),
        TranslateResult(
          translatedText = "<x id=\"tolgee-number\">0,5</x> psa",
          actualPrice = 100,
          usedService = MtServiceType.GOOGLE,
        ),
        TranslateResult(
          translatedText = "<x id=\"tolgee-number\">10</x> psů",
          actualPrice = 100,
          usedService = MtServiceType.GOOGLE,
        ),
      )
  }

  private fun mockApplicationContext(): ApplicationContext {
    val appContextMock = mock<ApplicationContext>()
    val entityManagerMock = EntityManager::class.mockIntoAppContext(appContextMock)

		mockQueryResult(entityManagerMock, mutableListOf(preparedKey)) {
      this.contains("new io.tolgee.service.machineTranslation.KeyForMt")
    }

    val mtServiceManagerMock = MtServiceManager::class.mockIntoAppContext(appContextMock)
    val firstResult = mtServiceManagerResults.firstOrNull()
    val rest = mtServiceManagerResults.drop(1)
    whenever(mtServiceManagerMock.translate(any())).thenReturn(firstResult, *rest.toTypedArray())

    val languageServiceMock = LanguageService::class.mockIntoAppContext(appContextMock)
    whenever(languageServiceMock.getProjectLanguages(any())).thenReturn(
      listOf(
        LanguageDto(0, tag = "en", base = true),
        LanguageDto(id = 1, tag = "cs"),
      ),
    )

    mockQueryResult(entityManagerMock, mutableListOf<ExampleItem>()) {
      this.contains("io.tolgee.component.machineTranslation.metadata.ExampleItem")
    }

    val translationMemoryServiceMock = TranslationMemoryService::class.mockIntoAppContext(appContextMock)

    doAnswer {
      Page.empty<TranslationMemoryItemView>()
    }
      .whenever(
        translationMemoryServiceMock,
      ).getSuggestions(
        any<String>(),
        any<Boolean>(),
        notNull(),
        any<LanguageDto>(),
        any<Pageable>(),
      )

    val bigMetaServiceMock = BigMetaService::class.mockIntoAppContext(appContextMock)
    whenever(bigMetaServiceMock.getCloseKeyIds(any())).thenReturn(emptyList())

    val projectServiceMock = ProjectService::class.mockIntoAppContext(appContextMock)
    val projectDtoMock = mock(ProjectDto::class.java)
    whenever(projectServiceMock.getDto(any())).thenReturn(projectDtoMock)

    val mtGlossaryTermsProviderMock = MtGlossaryTermsProvider::class.mockIntoAppContext(appContextMock)
    whenever(mtGlossaryTermsProviderMock.getGlossaryTerms(any(), any(), any(), any()))
      .thenReturn(emptySet())

		// Add them so we don't trigger NPE during bean lookups
		GoogleTranslationProvider::class.mockIntoAppContext(appContextMock)
		LlmTranslationProvider::class.mockIntoAppContext(appContextMock)

    return appContextMock
  }

  @Suppress("SqlSourceToSinkFlow")
  private fun <T> mockQueryResult(
    entityManagerMock: EntityManager,
    result: MutableList<T>,
    queryMatcher: String.() -> Boolean = { true },
  ) {
    val queryMock = mock<TypedQuery<T>>()
    whenever(
      entityManagerMock.createQuery(
        argThat<String> {
          queryMatcher(this)
        },
        any<Class<T>>(),
      ),
    ).thenReturn(queryMock)
    whenever(queryMock.resultList).thenReturn(result)
    whenever(queryMock.setParameter(any<String>(), any())).thenReturn(queryMock)
  }

  private fun getMtTranslatorContext(): MtTranslatorContext {
    val applicationContext = mockApplicationContext()
    val context = MtTranslatorContext(0, applicationContext, false)
    context.enabledServices[1L] =
      setOf(
        MtServiceInfo(
          MtServiceType.GOOGLE,
          formality = Formality.FORMAL,
        ),
        MtServiceInfo(
          MtServiceType.PROMPT,
          formality = Formality.FORMAL,
        ),
      )
    return context
  }

	private inline fun <reified T : Any> KClass<T>.mockIntoAppContext(appContext: ApplicationContext): T {
		val mock = mock<T>()
		whenever(appContext.getBean(T::class.java)).thenReturn(mock)
		return mock
	}
}
