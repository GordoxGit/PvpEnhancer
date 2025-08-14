# Changelog

## 2.2.1
- Implémentation du "Guidage par Intention" permettant de sculpter le knockback selon les mouvements de la caméra.

## 2.2.0
- Le son de dégât subi par la victime est désormais dynamique et suit le rythme du combo de l'attaquant.
- Suppression complète de la mécanique d'attaque de balayage ("swoosh").
- Le cooldown d'attaque post-1.9 a été entièrement désactivé pour une expérience 1.8 authentique.

## 2.1.0
- Implémentation d'un système de feedback audio dynamique où la tonalité des sons d'impact augmente avec les combos.

## 2.0.1
- Correction d'un crash critique de division par zéro (IllegalArgumentException: x not finite).
- Implémentation du système de Résonance Rythmique pour moduler la pureté physique de l'impact en fonction du tempo du joueur.
- Versionnage du projet ramené à 2.x pour une nouvelle base stable.

## 3.0.1 (Hotfix)
- Correction d'un crash critique (IllegalArgumentException: x not finite) causé par la normalisation d'un vecteur de vélocité nul.

## 3.0.0
- Added contextual movement and timing analysis to modulate knockback using contextual impact physics.
