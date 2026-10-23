"""Content of the "Green Streets" demo talk.

Everything here is fictional: the paper, the journal, the data and the five presenters.
It mirrors the structure of a real co-presented paper review (host hand-overs, a two-person
dialogue slide, solo slides) so the EarCue conversion skill can be tested end to end.
"""

TITLE = "Green Streets at Night"
TITLE_ZH = "夜晚的綠色街道"
SUBTITLE = "PAPER TALK · JOURNAL OF IMAGINARY URBAN STUDIES 2025"
TAGLINE = "Do street trees keep summer nights cooler?"
CITATION = ("Lin, R., & Okafor, T. (2025). Green Streets at Night: Tree Canopy and Night-Time Heat in Dense Cities. "
            "Journal of Imaginary Urban Studies, 12, 101. (Fictional paper created for EarCue testing.)")

PEOPLE = {
    "P1": "Alex Rivera",
    "P2": "Mina Park",
    "P3": "Leo Novak",
    "P4": "Sara Ito",
    "P5": "Omar Haddad",
}
ROLES = "P1 Alex Rivera, host · P2 Mina Park, background · P3 Leo Novak, method · P4 Sara Ito, results · P5 Omar Haddad, advice"
ROLES_ZH = "分工：P1 Alex Rivera 主持人 · P2 Mina Park 背景 · P3 Leo Novak 研究方法 · P4 Sara Ito 研究結果 · P5 Omar Haddad 實務建議"


def who(code):
    return f"{code} {PEOPLE[code]}"


# Each slide: id, part, part_zh, title, title_zh, speakers, minutes, core question (en, zh),
# key points [(en, zh)], handover (host question or None), mode ("one" or "two"), lines [(speaker code, en, zh)]
SLIDES = [
    dict(
        id="P1", part="Opening", part_zh="開場", kicker="OPENING",
        title="Green Streets at Night", title_zh="夜晚的綠色街道",
        speakers=["P1"], minutes=1.5,
        core=("What is the paper about, and how will we present it?", "這篇論文在講什麼？我們怎麼報告？"),
        points=[("Paper: Green Streets at Night (2025)", "論文：夜晚的綠色街道（2025）"),
                ("One sentence: trees cool the night, but only where they meet", "一句話：樹會讓夜晚變涼，但要連成一片才有用"),
                ("Four parts: background, method, results, advice", "四段：背景 → 方法 → 結果 → 建議")],
        handover=None, mode="one",
        lines=[
            ("P1", "Good afternoon, everyone. Five of us will walk you through one paper today. I am Alex, your host.",
             "大家午安。今天我們五個人要帶大家讀一篇論文，我是主持人 Alex。"),
            ("P1", "Mina starts with the background. Leo explains the method, Sara shows the results, and Omar ends with advice for cities.",
             "Mina 講背景，Leo 講方法，Sara 講結果，最後 Omar 給城市一些建議。"),
            ("P1", "The paper asks a simple question: do street trees keep summer nights cooler? Mina, let's start with something we all feel.",
             "這篇論文問了一個簡單的問題：行道樹能讓夏天的夜晚比較涼嗎？Mina，我們從大家都有感覺的事情開始吧。"),
        ],
    ),
    dict(
        id="P2", part="Part 1 Background", part_zh="第一部　背景", kicker="BACKGROUND",
        title="Why are summer nights getting hotter?", title_zh="為什麼夏天的夜晚越來越熱？",
        speakers=["P2", "P1"], minutes=2.5,
        core=("Why do city nights stay hot?", "為什麼城市的夜晚一直很熱？"),
        points=[("Poll: who could not sleep last summer?", "先舉手：去年夏天有沒有熱到睡不著？"),
                ("Concrete stores heat by day and gives it back at night", "水泥白天吸熱、晚上放熱"),
                ("Hot nights hurt sleep and health", "熱夜影響睡眠與健康")],
        handover=("P1", "Mina, let's start with something we all feel.", "Mina，我們從大家都有感覺的事情開始吧。"),
        mode="two",
        lines=[
            ("P1", "Quick poll first. Raise your hand if you could not sleep because of the heat last summer. That is a lot of hands.",
             "先來個小調查。去年夏天曾經熱到睡不著的人請舉手。哇，很多人。"),
            ("P2", "I am one of them. Alex, did you know the night is where cities and the countryside differ most?",
             "我也是其中一個。Alex，你知道城市和鄉下差最多的其實是晚上嗎？"),
            ("P1", "Really? I thought the afternoon was the worst part.", "真的嗎？我以為下午才是最熱的時候。"),
            ("P2", "The afternoon is hot everywhere. But after sunset, the countryside cools down fast. The city does not.",
             "下午到處都熱。但太陽下山以後，鄉下很快就涼了，城市卻不會。"),
            ("P1", "Why not?", "為什麼？"),
            ("P2", "Concrete and asphalt store heat during the day. At night they slowly give it back, like a warm stone in your hand.",
             "水泥和柏油白天把熱存起來，晚上再慢慢放出來，就像手裡握著一顆溫熱的石頭。"),
            ("P2", "Scientists call this the urban heat island. In some cities, nights are now five degrees warmer than nearby fields.",
             "科學家把這叫做都市熱島。有些城市的夜晚，現在比附近的田野熱上五度。"),
            ("P1", "So it is not just about comfort?", "所以不只是舒不舒服的問題？"),
            ("P2", "No. Hot nights mean poor sleep, and older people are at real risk. That is why this paper looks at the night, not the day.",
             "不只。熱夜代表睡不好，年長者更有真正的健康風險。這就是為什麼這篇論文看的是夜晚，而不是白天。"),
        ],
    ),
    dict(
        id="P3", part="Part 1 Background", part_zh="第一部　背景", kicker="BACKGROUND",
        title="Trees as the city's air conditioner", title_zh="樹是城市的冷氣機",
        speakers=["P2"], minutes=2,
        core=("How can trees cool a street?", "樹怎麼讓一條街變涼？"),
        points=[("Shade: less heat stored by day", "遮蔭：白天少存一點熱"),
                ("Water: leaves release water and cool the air", "水分：葉子蒸散，讓空氣降溫"),
                ("Open question: does a single tree help at night?", "未知：一棵樹在晚上有用嗎？")],
        handover=("P1", "So how do trees fit in, Mina?", "那樹在這裡扮演什麼角色，Mina？"),
        mode="one",
        lines=[
            ("P2", "Trees help in two ways. First, shade. A shaded road stores much less heat during the day, so it has less to give back at night.",
             "樹有兩種幫忙的方式。第一是遮蔭。有遮蔭的路面白天存的熱少很多，晚上要放的熱也就少。"),
            ("P2", "Second, water. Leaves release water into the air. That works like sweat on your skin, and it cools the air around the tree.",
             "第二是水分。葉子會把水分釋放到空氣中，就像皮膚上的汗，讓樹周圍的空氣變涼。"),
            ("P2", "We know this works in the afternoon. But at night there is no sun to block, and leaves release much less water.",
             "我們知道這在下午有效。但晚上沒有太陽可以擋，葉子釋放的水分也少很多。"),
            ("P2", "So the open question was: do trees still cool a street after dark? And does one tree help, or do you need many?",
             "所以還沒有答案的問題是：天黑以後，樹還能讓街道變涼嗎？一棵樹就有用，還是需要很多棵？"),
        ],
    ),
    dict(
        id="P4", part="Part 1 Background", part_zh="第一部　背景", kicker="BACKGROUND",
        title="Two research questions", title_zh="兩個研究問題",
        speakers=["P2"], minutes=1,
        core=("What do the authors want to know?", "作者想知道什麼？"),
        points=[("RQ1: does tree cover lower night-time heat?", "RQ1：樹冠覆蓋能降低夜間溫度嗎？"),
                ("RQ2: does the layout of trees matter?", "RQ2：樹怎麼排列有差嗎？"),
                ("Next: how they measured it", "下一頁：他們怎麼測量")],
        handover=None, mode="one",
        lines=[
            ("P2", "The authors ask two questions. RQ1: does more tree cover on a street mean a cooler night on that street?",
             "作者問了兩個問題。RQ1：一條街的樹冠越多，那條街的夜晚就越涼嗎？"),
            ("P2", "RQ2: does it matter how the trees are placed? For example, a few big trees in a row, or many small trees spread out?",
             "RQ2：樹怎麼種有差嗎？例如一排幾棵大樹，還是很多小樹分散種？"),
            ("P2", "To answer them, they put sensors on sixty streets for a whole summer. Leo will tell you how.",
             "為了回答這些問題，他們在六十條街上放了一整個夏天的感測器。接下來請 Leo 說明他們怎麼做。"),
        ],
    ),
    dict(
        id="P5", part="Part 2 Method", part_zh="第二部　方法", kicker="METHOD",
        title="Sixty streets, one summer", title_zh="六十條街，一個夏天",
        speakers=["P3"], minutes=2,
        core=("How did they measure night heat?", "他們怎麼測量夜晚的熱？"),
        points=[("60 streets, 90 nights, a sensor every 50 meters", "60 條街、90 個夜晚、每 50 公尺一個感測器"),
                ("Tree cover measured from summer satellite photos", "樹冠覆蓋用夏季衛星照片測量"),
                ("Compared streets with similar buildings and traffic", "比較建築與車流相近的街道")],
        handover=("P2", "Leo, how did they measure it?", "Leo，他們是怎麼測量的？"),
        mode="one",
        lines=[
            ("P3", "Thanks, Mina. The team picked sixty streets in one large city. Each street got a small sensor every fifty meters.",
             "謝謝 Mina。研究團隊在一座大城市挑了六十條街，每條街每五十公尺裝一個小感測器。"),
            ("P3", "The sensors recorded the air temperature every ten minutes for ninety summer nights.",
             "感測器每十分鐘記錄一次氣溫，連續九十個夏夜。"),
            ("P3", "They measured tree cover from satellite photos, and they only compared streets with similar buildings and traffic.",
             "樹冠覆蓋是用衛星照片量的，而且只比較建築和車流相近的街道。"),
            ("P3", "That way, the difference between streets comes from the trees, not from the cars. Sara has the results.",
             "這樣街道之間的差異才會來自樹，而不是來自車子。結果就交給 Sara。"),
        ],
    ),
    dict(
        id="P6", part="Part 3 Results", part_zh="第三部　結果", kicker="RESULTS",
        title="Connected shade wins", title_zh="連成一片的樹蔭最有效",
        speakers=["P4"], minutes=2,
        core=("What did the sensors show?", "感測器量到了什麼？"),
        points=[("Streets with 40% cover were 1.8 °C cooler at night", "樹冠 40% 的街道，夜間低 1.8 °C"),
                ("Connected canopies beat scattered trees", "連續樹冠勝過零散的樹"),
                ("Below 15% cover: almost no effect", "樹冠低於 15%：幾乎沒效果")],
        handover=("P3", "Sara has the results.", "結果就交給 Sara。"),
        mode="one",
        lines=[
            ("P4", "Here is the main result. Streets with about forty percent tree cover were 1.8 degrees cooler at night than bare streets.",
             "這是主要結果。樹冠大約百分之四十的街道，夜間比光禿的街道低 1.8 度。"),
            ("P4", "But the layout mattered even more. When the tree tops touched and made one long roof, the cooling almost doubled.",
             "但排列方式更重要。當樹冠互相連接、形成一整條長長的屋頂時，降溫效果幾乎加倍。"),
            ("P4", "Scattered trees helped much less. And below fifteen percent cover, the sensors could hardly see any effect at all.",
             "零散的樹幫助小很多。樹冠低於百分之十五時，感測器幾乎量不到任何效果。"),
        ],
    ),
    dict(
        id="P7", part="Part 4 Advice", part_zh="第四部　建議", kicker="ADVICE",
        title="Plant in lines, not in dots", title_zh="種成一條線，而不是一個個點",
        speakers=["P5"], minutes=2,
        core=("What should cities do?", "城市應該怎麼做？"),
        points=[("Join existing trees into green corridors", "把現有的樹連成綠色廊道"),
                ("Aim for at least 40% cover on hot streets", "最熱的街道至少要有 40% 樹冠"),
                ("Limits: one city, one summer", "限制：只有一座城市、一個夏天")],
        handover=("P1", "Omar, what should cities do with this?", "Omar，城市可以怎麼運用這些發現？"),
        mode="one",
        lines=[
            ("P5", "My advice for cities is simple: plant in lines, not in dots. Join the trees you already have into green corridors.",
             "我給城市的建議很簡單：種成一條線，而不是一個個點。把現有的樹連成綠色廊道。"),
            ("P5", "Start with the hottest streets, and aim for at least forty percent cover there.",
             "從最熱的街道開始，目標是至少百分之四十的樹冠覆蓋。"),
            ("P5", "We also see limits. The study covers one city and one summer, so other climates may give different numbers.",
             "我們也看到限制。這個研究只有一座城市、一個夏天，其他氣候可能會得到不同的數字。"),
        ],
    ),
    dict(
        id="P8", part="Closing", part_zh="結語", kicker="TAKEAWAY",
        title="Shade together, sleep better", title_zh="一起遮蔭，一起好眠",
        speakers=["P1"], minutes=1,
        core=("What should the audience remember?", "觀眾該記住什麼？"),
        points=[("Trees cool nights when their canopies connect", "樹冠連在一起，夜晚才會變涼"),
                ("Thank you · questions welcome", "謝謝大家 · 歡迎提問")],
        handover=None, mode="one",
        lines=[
            ("P1", "To sum up: trees can cool our summer nights, but only when they work together. Thank you for listening. We are happy to take your questions.",
             "總結一下：樹可以讓夏天的夜晚變涼，但要它們一起合作才行。謝謝大家聆聽，歡迎提問。"),
        ],
    ),
]
