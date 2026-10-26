package com.example.data.ai

data class ScriptTemplate(
    val id: String,
    val title: String,
    val category: String,
    val iconEmoji: String,
    val description: String,
    val defaultText: String
)

object ScriptTemplates {
    val templates = listOf(
        ScriptTemplate(
            id = "pub_flash",
            title = "Spot Publicitaire Flash",
            category = "Marketing",
            iconEmoji = "📢",
            description = "Idéal pour teaser un produit ou une promotion",
            defaultText = "Attention ! Aujourd'hui seulement, profitez d'une remise exceptionnelle de moins 50% sur toute notre nouvelle collection. Rendez-vous immédiatement en boutique ou sur notre site web !"
        ),
        ScriptTemplate(
            id = "podcast_intro",
            title = "Intro Podcast & Vidéo",
            category = "Média",
            iconEmoji = "🎙️",
            description = "Accroche percutante pour captiver les auditeurs",
            defaultText = "Bienvenue dans ce nouvel épisode ! Installez-vous confortablement, car aujourd'hui nous explorons ensemble les secrets les plus fascinants de la créativité et de la technologie."
        ),
        ScriptTemplate(
            id = "repondeur_pro",
            title = "Standard & Répondeur Pro",
            category = "Business",
            iconEmoji = "📞",
            description = "Message d'accueil d'entreprise soigné et courtois",
            defaultText = "Bonjour et bienvenue chez Voix Studio. Toutes nos lignes sont actuellement occupées. Veuillez laisser votre nom et vos coordonnées après le bip sonore, nous vous rappellerons dans les plus brefs délais."
        ),
        ScriptTemplate(
            id = "cinema_trailer",
            title = "Bande-Annonce Épique",
            category = "Cinéma",
            iconEmoji = "🎬",
            description = "Narration dramatique et cinématographique",
            defaultText = "Dans un univers où l'oubli a tout effacé, un héros solitaire se dresse face à son destin. Préparez-vous à vivre l'aventure ultime. Prochainement au cinéma."
        ),
        ScriptTemplate(
            id = "meditation_zen",
            title = "Relaxation & Pleine Conscience",
            category = "Bien-être",
            iconEmoji = "🧘",
            description = "Script apaisant pour séance guidée",
            defaultText = "Fermez doucement les yeux. Prenez une grande inspiration par le nez, puis expirez lentement par la bouche. Ressentez cette vague de sérénité qui traverse votre esprit."
        ),
        ScriptTemplate(
            id = "conte_enfant",
            title = "Conte pour Enfants",
            category = "Jeunesse",
            iconEmoji = "🧸",
            description = "Histoire magique et douce pour le coucher",
            defaultText = "Au sommet de la colline aux papillons dorés vivait Barnabé, un petit dragon qui avait peur du noir. Mais un soir, la lune lui confia un secret extraordinaire..."
        ),
        ScriptTemplate(
            id = "pitch_startup",
            title = "Pitch Investisseur",
            category = "Business",
            iconEmoji = "💡",
            description = "Présentation claire, rapide et convaincante",
            defaultText = "Saviez-vous que 80% des créateurs perdent des heures sur leurs voix off ? Avec notre solution, créez des voix sur mesure en quelques secondes. Simple, rapide et accessible."
        )
    )

    fun enhanceScriptLocally(rawText: String, style: String): String {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return "Texte vide"
        return when (style) {
            "energetic" -> "🔥 Incroyable ! $trimmed N'attendez plus, découvrez-le dès maintenant !"
            "cinema" -> "Dans les profondeurs du temps... $trimmed Le monde ne sera plus jamais le même."
            "zen" -> "Prenez un instant... $trimmed Laissez ces mots résonner en vous."
            "professional" -> "Information importante. $trimmed Merci pour votre attention et votre confiance."
            else -> trimmed
        }
    }
}
