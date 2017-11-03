package org.jetbrains.demo.thinkter

import assertk.assertions.contains
import assertk.assertions.containsAll
import assertk.assertions.isEqualTo
import io.mockk.*
import io.mockk.junit.MockKJUnit4Runner
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.demo.thinkter.dao.ThinkterStorage
import org.jetbrains.demo.thinkter.model.IndexResponse
import org.jetbrains.demo.thinkter.model.PollResponse
import org.jetbrains.demo.thinkter.model.Thought
import org.jetbrains.demo.thinkter.model.User
import org.jetbrains.ktor.cio.ByteBufferWriteChannel
import org.jetbrains.ktor.html.HtmlContent
import org.jetbrains.ktor.http.HttpHeaders
import org.jetbrains.ktor.http.HttpMethod
import org.jetbrains.ktor.locations.Locations
import org.jetbrains.ktor.routing.HttpHeaderRouteSelector
import org.jetbrains.ktor.routing.HttpMethodRouteSelector
import org.jetbrains.ktor.routing.Routing
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.Charset
import java.time.*
import java.time.format.DateTimeFormatter

@RunWith(MockKJUnit4Runner::class)
class IndexKtTest {
    val route = mockk<Routing>()
    val dao = mockk<ThinkterStorage>()
    val locations = mockk<Locations>()

    val getHtmlIndex = DslRouteSlot()
    val getJsonIndex = DslRouteSlot()
    val getJsonPoll = DslRouteSlot()

    @Before
    fun setUp() {
        route.mockDsl(locations) {
            mockSelect(HttpHeaderRouteSelector(HttpHeaders.Accept, "text/html")) {
                mockObj<Index> {
                    mockSelect(HttpMethodRouteSelector(HttpMethod.Get)) {
                        captureHandle(getHtmlIndex)
                    }
                }
            }
            mockSelect(HttpHeaderRouteSelector(HttpHeaders.Accept, "application/json")) {
                mockObj<Index> {
                    mockSelect(HttpMethodRouteSelector(HttpMethod.Get)) {
                        captureHandle(getJsonIndex)
                    }
                }
                mockObj<Poll> {
                    mockSelect(HttpMethodRouteSelector(HttpMethod.Get)) {
                        captureHandle(getJsonPoll)
                    }
                }
            }
        }

        route.index(dao)

    }

    @Test
    fun testGetIndexHtml() {
        getHtmlIndex.issueCall(locations, Index()) { handle ->
            val html = slot<String>()
            coEvery { respond(any()) } answers {
                runBlocking {
                    val htmlContent = firstArg<HtmlContent>()
                    val channel = ByteBufferWriteChannel()
                    htmlContent.writeTo(channel)
                    html.captured = channel.toString(Charset.defaultCharset())
                }
                nothing
            }

            handle()

            assertk.assert(html.captured!!)
                    .contains("<title>Thinkter</title>")
        }
    }

    @Test
    fun testGetIndexJson() {
        getJsonIndex.issueCall(locations, Index()) { handle ->
            mockSessionReturningUser(dao)

            every { dao.top(10) } returns (1..10).toList()

            every { dao.latest(10) } returns (1..10).toList()

            every { dao.getThought(any()) } answers {
                Thought(firstArg(),
                        "user" + firstArg(),
                        "text",
                        "date",
                        null)
            }

            coEvery { respond(any()) } just Runs

            every { response.pipeline.intercept(any(), any()) } just Runs

            handle()

            coVerify {
                respond(assert<IndexResponse>(msg = "response should have top and latest with ids from one to ten") {
                    val oneToTen = (1..10).toList()

                    it!!.top.map { it.id }.containsAll(oneToTen)
                        && it.latest.map { it.id }.containsAll(oneToTen)
                })
            }
        }
    }

    @Test
    fun testGetPollJsonBlank() {
        getJsonPoll.issueCall(locations, Poll("")) { handle ->
            coEvery { respond(any()) } just Runs

            handle()

            coVerify {
                respond(assert<PollResponse> { it!!.count == "0" })
            }
        }
    }

    @Test
    fun testGetPollJsonOne() {
        checkPoll("9", "1")
    }

    @Test
    fun testGetPollJsonFive() {
        checkPoll("5", "5")
    }

    @Test
    fun testGetPollJsonTenPlus() {
        checkPoll("0", "10+")
    }

    private fun checkPoll(pollTime: String, responseCount: String) {
        getJsonPoll.issueCall(locations, Poll(pollTime)) { handle ->
            every { dao.latest(10) } returns (1..10).toList()
            every { dao.getThought(any()) } answers {
                Thought(firstArg(),
                        "userId",
                        "text",
                        formatDate(firstArg<Int>().toLong()),
                        null)
            }

            coEvery { respond(any()) } just Runs

            handle()

            coVerify {
                respond(assert<PollResponse> { it!!.count == responseCount })
            }
        }
    }

    private fun formatDate(date: Long): String {
        return  Instant.ofEpochMilli(date)
                .atZone(ZoneId.systemDefault())
                .toOffsetDateTime()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }
}

