# EMI-Reorganised
Adding tabs, Sable-style banners, item groups, a custom item order, an in-game editor, and other small changes to EMI.

<img width="696" height="285" alt="Preview" src="https://github.com/user-attachments/assets/4e2a431f-2daa-4db1-85f0-52e05e206157" />

## DISCLAIMERS
### Inspirations
This mod is heavily based on [Recreative](https://github.com/evanbones/Recreative) and [Reliable-EMI](https://github.com/evanbones/Reliable-EMI), adding a very similar in-game editor and item groups.
The key difference from Recreative is that Reorganised adds its own custom item list, establishing its own tabs without touching the creative ones.
### !!! AI USE !!!
This mod is almost entirely coded by Devin AI. I am an entry-level coder at best, and although I am learning, a mod like this is well beyond my abilities. Therefore, I can't guarantee the quality of code in this mod. I also can't guarantee that I will be bugfixing/updating/porting this mod in the future (due to the cost of a Devin subscription), but that's also not an impossibility.

I'm not a fan of low-effort AI-generated mods. In this case, I performed a lot of testing and reworking of features to ensure the mod is a smooth and usable experience. All the same, I won't be posting this mod to Curseforge/Modrinth for this reason.

I wanted this mod for personal use in a modpack shared only with friends; however, I think the mod concept is strong enough to be worth sharing here, even if only to inspire a genuinely proficient mod maker to create something similar. If you wish to fork this repository and publish your own version, feel free.

## Minecraft Version & Relations
Right now, EMI Reorganised is exclusively for NeoForge 1.21.1 and depends on EMI. 

Optional dependencies:
 - Create (schematic integration)
 - Sable (banner integration)
 - YetAnotherConfigLib (in-game config editing)

## Reorganising The Inventory
The core goal of this mod is to improve the readability and usability of the often MASSIVE EMI/NEI-style item-list interface for large modpacks. This allows pack-makers to ensure their modpacks feel more like a single consistent game rather than a list of cobbled-together mods.
For this goal, EMI has been chosen, as it offers the vanilla visual style (which is on by default with Reorganised, but it can be turned off) which helps the interface feel more in line with the game as a whole.

EMI Reorganised achieves this goal in the following ways:
1. Establishing its own item index through a series of JSON files in the mod's config folder for EMI to pull from.
2. Adding customisable creative-like tabs to the EMI index (each tab has its own JSON file in the config).
3. Adding customisable Sable-like banners to the item list to visually break up the wall of items (automatically imports banners from Sable mods).
4. Adding customisable item groups inspired by Reliable EMI, which can significantly shorten the item list (can be populated from tags or regex).
5. Adding an in-game editor for all of these customisable elements, aiming to feel natural and intuitive to use with tools like click_drag box selection, CTRL+Z/Y/X/C/V commands, popups for editing tabs, banners, groups, and much more.
6. Replacing the creative UI with the survival UI and instead using cheat mode in EMI for the same functionality. (The creative tabs are unedited, so this is done for the sake of cohesion)(This is toggleable)
7. Adding tabs to the EMI Favourites panel (on the left by default) that the player can add/remove/reorganise while playing
8. Integrating with Create schematics; by pressing the favourite key ('A' by default) over a schematic, a new tab is added to the favourites panel with a list of required items.
9. Adding an extensive config to adjust most features added by the mod

## Feature Showcase
### Inventory
<img width="1918" height="1017" alt="Inventory" src="https://github.com/user-attachments/assets/657db537-d211-4e34-9db3-1a9ed4a7d27c" />

### Hideable Side Panels
<img width="1919" height="1018" alt="Inventory_Hidden_EMI" src="https://github.com/user-attachments/assets/dc71fe1f-3cde-4dc9-919a-b47f94444d23" />

### Create Schematic Lists
<img width="1909" height="1016" alt="Schematic List" src="https://github.com/user-attachments/assets/4cba11f1-039a-439b-a89d-6644c30019b9" />

### Editor
<img width="1919" height="1015" alt="Editor" src="https://github.com/user-attachments/assets/cee4bf2c-8908-4d0c-8e80-374e06a1384a" />

### Right-Click Context Menus
<img width="1917" height="1016" alt="Right_Click_Context_Menu" src="https://github.com/user-attachments/assets/c5923c7d-c11e-4ad2-a3d7-67d67672ea50" />

### New Banner Popup
<img width="1919" height="1013" alt="New_Banner_Popup" src="https://github.com/user-attachments/assets/98267bd8-0c5d-43f4-86b3-5e9761af1002" />

### New Item Group Popup
<img width="1919" height="1016" alt="New_Item_Group_Popup" src="https://github.com/user-attachments/assets/44eb7512-0c51-4951-bd5d-b910bd231d07" />
