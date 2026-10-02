package com.example.livora.data.dictionary

import com.example.livora.data.model.ClozeLevel

data class ClozeItem(
    val level: ClozeLevel,
    val text: String,
    val distractors: List<String>
)

object ClozeBank {

    private fun sentence(text: String, vararg distractors: String) =
        ClozeItem(ClozeLevel.Sentence, text, distractors.toList())

    private fun paragraph(text: String, vararg distractors: String) =
        ClozeItem(ClozeLevel.Paragraph, text, distractors.toList())

    val items: List<ClozeItem> = listOf(
        sentence(
            "Each of the students is sleeping during the class, [[therefore]] they get bad marks.",
            "although", "whereas", "in conclusion", "despite", "for example"
        ),
        sentence(
            "Public transport is cheap and reliable. [[however]], many commuters still drive to work.",
            "therefore", "because", "in conclusion", "firstly"
        ),
        sentence(
            "Urban wages are high, [[whereas]] rural wages remain low.",
            "because", "therefore", "in conclusion", "despite"
        ),
        sentence(
            "Many families move abroad [[because]] jobs are scarce at home.",
            "although", "whereas", "however", "in conclusion"
        ),
        sentence(
            "Flights were cancelled [[because of]] the storm.",
            "because", "although", "whereas", "in order to"
        ),
        sentence(
            "[[although]] fuel is expensive, car use keeps growing.",
            "because", "therefore", "for example", "in conclusion"
        ),
        sentence(
            "[[despite]] the high cost, many people travel abroad every year.",
            "because", "whereas", "therefore", "although"
        ),
        sentence(
            "Technology saves time and improves health care. [[in conclusion]], its benefits outweigh the risks.",
            "for example", "firstly", "whereas", "because"
        ),
        sentence(
            "Exercise lowers stress. [[moreover]], it improves sleep.",
            "however", "whereas", "despite", "because"
        ),
        sentence(
            "Cars are convenient. [[on the other hand]], they damage air quality.",
            "therefore", "in conclusion", "for example", "firstly"
        ),
        sentence(
            "Many cities struggle with traffic, [[for example]] Jakarta and Manila.",
            "whereas", "therefore", "although", "in conclusion"
        ),
        sentence(
            "Governments tax sugar [[in order to]] reduce obesity.",
            "because", "although", "despite", "whereas"
        ),
        sentence(
            "Cities can cut pollution [[provided that]] they limit private cars.",
            "whereas", "despite", "in conclusion", "however"
        ),
        sentence(
            "Most experts agree that sugar is harmful. [[in fact]], it is linked to many serious diseases.",
            "whereas", "despite", "because", "although"
        ),
        sentence(
            "Youth unemployment is high, [[especially]] in rural areas.",
            "because", "whereas", "although", "therefore"
        ),
        sentence(
            "The chart shows that sales doubled between 2010 and 2020. [[overall]], the trend was upward.",
            "for example", "because", "whereas", "despite"
        ),
        sentence(
            "Rents increased sharply last year, and [[as a result]] fewer young people can afford to move out.",
            "although", "despite", "whereas", "in conclusion"
        ),
        sentence(
            "[[nowadays]], most people shop online.",
            "in the past", "whereas", "therefore", "despite"
        ),
        sentence(
            "[[in the past]], families lived closer together.",
            "nowadays", "therefore", "because", "whereas"
        ),
        sentence(
            "Online learning saves money. [[at the same time]], it gives students more flexibility.",
            "in conclusion", "whereas", "despite", "because"
        ),
        sentence(
            "The company hired new staff, and [[after that]] opened two more branches.",
            "whereas", "despite", "although", "because of"
        ),
        sentence(
            "Trees absorb carbon [[as well as]] cool the streets.",
            "whereas", "despite", "because", "although"
        ),
        sentence(
            "I agree with this view [[to some extent]].",
            "because of", "whereas", "despite", "in order to"
        ),
        sentence(
            "[[although]] many people enjoy city life, rents are rising fast, [[therefore]] some residents are moving out.",
            "despite", "in conclusion", "for example"
        ),
        paragraph(
            "Remote work has become popular in recent years. [[firstly]], employees save time because they do not commute. " +
                "[[moreover]], companies spend less money on office space. " +
                "[[however]], some managers worry that teamwork suffers, [[whereas]] others believe that online tools solve this problem. " +
                "[[in conclusion]], remote work brings clear benefits when it is managed carefully.",
            "despite", "because of", "in order to"
        ),
        paragraph(
            "Many students feel stressed [[because of]] heavy homework. " +
                "[[as a result]], they sleep less and struggle to concentrate. " +
                "[[despite]] these problems, many schools still set homework every night [[in order to]] raise exam scores. " +
                "[[however]], research suggests that long hours of study do not always improve results.",
            "whereas", "firstly", "provided that"
        ),
        paragraph(
            "Zoos remain controversial. Supporters say they protect rare species, [[whereas]] critics argue that animals suffer in small cages. " +
                "[[for example]], elephants often develop stress in captivity. " +
                "[[nevertheless]], many zoos now run breeding programmes [[in order to]] return animals to the wild. " +
                "[[in conclusion]], zoos can be justified only if they put animal welfare first.",
            "because of", "firstly", "despite"
        ),
        paragraph(
            "Some people believe that university education should be free. " +
                "[[in my opinion]], this idea has clear advantages. " +
                "[[firstly|for example]], free tuition helps talented students from poor families. " +
                "[[for example|firstly]], graduates earn more and pay higher taxes later. " +
                "[[nevertheless]], I agree with critics [[to some extent]], because the cost to taxpayers is high.",
            "despite", "whereas", "because of"
        ),
        paragraph(
            "Plastic waste is polluting the oceans. [[as a result]], many sea animals die every year. " +
                "Governments could reduce the problem [[if]] they banned single-use bags. " +
                "[[at the same time|in conclusion]], citizens should recycle more, [[as well as]] buy fewer packaged goods. " +
                "[[in conclusion|at the same time]], both governments and individuals must act.",
            "whereas", "despite", "although"
        ),
        paragraph(
            "Children spend several hours online every day. [[therefore|however]], many parents worry about their eyesight and sleep. " +
                "[[however]], technology also helps them learn. " +
                "[[for example]], educational apps teach maths and languages. " +
                "[[overall]], the benefits are greater than the risks when screen time is controlled.",
            "whereas", "despite", "in order to"
        )
    )
}
