package com.rentmate.app.data

/** The household's landlord, as stored on the `households` row (RM-0009). */
data class LandlordContact(
    val householdName: String,
    val landlordName: String? = null,
    val landlordEmail: String? = null
) {
    /** True when there is an address to send the email to. */
    val hasEmail: Boolean get() = !landlordEmail.isNullOrBlank()
}

/**
 * The translated pieces of the landlord email. Each template uses Android
 * `%1$s`-style placeholders, so the screen fills this from string resources and
 * the builder below stays a plain function that unit tests can call.
 */
data class LandlordEmailText(
    val subject: String,          // %1$s = household name
    val greetingNamed: String,    // %1$s = landlord name
    val greetingGeneric: String,
    val intro: String,            // %1$s = household name; already pluralised for the count
    val itemCategory: String,     // %1$s = category label
    val itemUrgency: String,      // %1$s = urgency label
    val itemLogged: String,       // %1$s = date
    val itemPhotos: String,       // heading above a request's photo links (RM-0029)
    val signOff: String,          // %1$s = household name
    val categoryLabel: (MaintenanceCategory) -> String,
    val urgencyLabel: (Urgency) -> String
)

/** A finished email, ready to hand to an ACTION_SENDTO intent. */
data class LandlordEmail(val to: String, val subject: String, val body: String)

/**
 * RM-0009: one itemised email listing every open fault, numbered, with its
 * category, urgency, description and the date it was logged.
 *
 * RM-0029: [photoLinks] maps a request ID to signed URLs for its photos; the
 * bucket is private, so the landlord opens them through these links (valid
 * for [com.rentmate.app.data.attachments.EMAIL_LINK_EXPIRY]).
 */
fun buildLandlordEmail(
    contact: LandlordContact,
    openRequests: List<MaintenanceRequest>,
    text: LandlordEmailText,
    photoLinks: Map<String, List<String>> = emptyMap(),
    formatDate: (Long) -> String
): LandlordEmail {
    val greeting = contact.landlordName?.takeIf { it.isNotBlank() }
        ?.let { text.greetingNamed.fill(it.trim()) }
        ?: text.greetingGeneric

    val items = openRequests.mapIndexed { index, request ->
        buildString {
            append("${index + 1}. ${request.title}\n")
            append("   ${text.itemCategory.fill(text.categoryLabel(request.category))}\n")
            append("   ${text.itemUrgency.fill(text.urgencyLabel(request.urgency))}\n")
            append("   ${text.itemLogged.fill(formatDate(request.createdAt))}\n")
            if (request.description.isNotBlank()) append("   ${request.description.trim()}\n")
            val links = photoLinks[request.id].orEmpty()
            if (links.isNotEmpty()) {
                append("   ${text.itemPhotos}\n")
                links.forEach { append("   $it\n") }
            }
        }
    }

    val body = buildString {
        append(greeting).append("\n\n")
        append(text.intro.fill(contact.householdName)).append("\n\n")
        items.forEach { append(it).append("\n") }
        append(text.signOff.fill(contact.householdName))
    }

    return LandlordEmail(
        to = contact.landlordEmail?.trim().orEmpty(),
        subject = text.subject.fill(contact.householdName),
        body = body
    )
}

/** Needs one @, a dot in the domain, a top-level domain of two or more characters and no whitespace. */
private val EMAIL = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]{2,}$""")

/** Loose check before saving the landlord's address: something@domain.tld, no spaces. */
fun isValidEmail(email: String): Boolean = EMAIL.matches(email.trim())

/** Fills the single `%1$s` placeholder of a translated template. */
private fun String.fill(value: String) = replace("%1\$s", value)
