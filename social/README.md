# nah.meter social images

Still images for Instagram stories (1080×1920) and posts (1080×1350, 4:5). Each one shows
nah.meter's verdict card docked over a delivery offer, on a real map of Toronto and the GTA. The
offers are made up; the restaurants, streets and routes are real.

```
npm install                 # Inter, for the offer card
pip install pyarrow shapely numpy
python3 scripts/build.py    # data/offers.out.json; the first run reads Overture from S3 (cached in data/)
node render.mjs             # out/story-NN.png and out/post-NN.png
```

`python3 scripts/build.py 03` or `node render.mjs 03` redoes one offer.

## How an offer is made

`offers.json` gives each offer:

- the restaurant: its exact name in Overture and a point near it
- where the courier is
- roughly where the drop-off is
- the number of orders and whether it's exclusive or an add-on
- a pay rate
- the verdict it should get

`scripts/build.py` then:

1. finds the restaurant in Overture's places;
2. snaps the courier to the nearest street, and the drop-off to the nearest corner of two named
   streets, which becomes the "X St & Y Ave, City" line;
3. routes courier → restaurant → drop-off on the real road graph (Overture transportation, from
   OpenStreetMap);
4. takes km from the route and minutes from a city pace: the drive × 1.3, plus 6 min for the
   pickup and 2 for the hand-off, close to the times on real cards;
5. sets the pay from the planned rate and scores it with a port of `core/Scorer.kt` and the card
   strings of `NahCopy` (default rules: $20/hr, $1/km, $5.50 minimum, 2 min per order on
   multi-order trips). The build stops if an offer doesn't get its planned verdict.

`template.html` draws the screen:

- the map, the route and the pins
- the verdict card, matching `BadgeOverlay` (Geist from the app's fonts, verdict colours from
  `ui/Nah.kt`)
- the offer card, laid out like the one couriers see, with no app name or logo

The post is a 4:5 crop that ends just under the trip line.

## Showcase video

`node render.mjs --layers --scale=2 05 01 03 06` renders four offers as phone-shaped layers in
`layers/`, at 2160 px wide so the 4K cut stays sharp:

- the screen without its verdict card or the Accept timer's fill
- the button's word alone
- the verdict card alone
- where each of them sits

`node scripts/copy-to-promo.mjs` hands the layers to `promo/`, where `src/Showcase.tsx` animates
them over the promo's audio (`npm run showcase-assets` in `promo/` runs both). Each of these offers
has a rate matching what its verdict clip says ("15", "30", "21 an hour"), so keep those rates if
you change them.

## Story layout

Stories keep the offer card 340 px above the bottom and the cards below 250 px from the top, so
Instagram's name row and reply bar don't cover them. The map fills the rest of the frame.

## Data and licences

- Map data: [Overture Maps Foundation](https://overturemaps.org), release `2026-10-21.0`, read
  straight from its public S3 bucket.
  - Roads and water come from OpenStreetMap, under the ODbL. Every image says "Map data ©
    OpenStreetMap contributors · Overture Maps".
  - Places are under CDLA Permissive 2.0.
- Fonts:
  - Geist (SIL OFL), from `app/src/main/res/font`
  - Inter (SIL OFL), from `@fontsource/inter`

## Posting

The offers are samples. Say so in the caption, and don't name or tag the delivery app. For
example:

> Sample offers, made-up numbers. nah.meter isn't affiliated with any delivery app.
