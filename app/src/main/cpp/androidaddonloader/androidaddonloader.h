/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2016-2016 CSSlayer <wengxt@gmail.com>
 * SPDX-FileCopyrightText: Copyright 2023-2024 Fcitx5 for Android Contributors
 * SPDX-FileComment: Modified from https://github.com/fcitx/fcitx5/blob/5.1.1/src/lib/fcitx/addonloader_p.h
 */

#ifndef FCITX5_ANDROID_ANDROIDADDONLOADER_H
#define FCITX5_ANDROID_ANDROIDADDONLOADER_H

#include <memory>
#include <stdexcept>
#include <string>
#include <string_view>
#include <unordered_map>
#include <utility>
#include <fcitx-utils/library.h>
#include <fcitx-utils/standardpath.h>
#include <fcitx-utils/stringutils.h>
#include <fcitx/addonfactory.h>
#include <fcitx/addoninfo.h>
#include <fcitx/addoninstance.h>
#include <fcitx/addonloader.h>

namespace fcitx {

namespace {
constexpr char FCITX_ADDON_FACTORY_ENTRY[] = "fcitx_addon_factory_instance";
}

class AndroidSharedLibraryFactory {
public:
    explicit AndroidSharedLibraryFactory(const AddonInfo &info, Library lib)
            : library_(std::move(lib)) {
        auto v2Name = stringutils::concat(FCITX_ADDON_FACTORY_ENTRY, "_", info.uniqueName());
        auto *funcPtr = library_.resolve(v2Name.data());
        if (!funcPtr) {
            funcPtr = library_.resolve(FCITX_ADDON_FACTORY_ENTRY);
        }
        if (!funcPtr) {
            throw std::runtime_error(library_.error());
        }
        auto func = Library::toFunction<AddonFactory *()>(funcPtr);
        factory_ = func();
        if (!factory_) {
            throw std::runtime_error("Failed to get a factory");
        }
    }

    AddonFactory *factory() { return factory_; }

private:
    Library library_;
    AddonFactory *factory_;
};

typedef std::unordered_map<std::string, std::unordered_set<std::string>> AndroidLibraryDependency;

class AndroidSharedLibraryLoader : public AddonLoader {
public:
    explicit AndroidSharedLibraryLoader(AndroidLibraryDependency dependency);

    [[nodiscard]] std::string type() const override { return "SharedLibrary"; }

    AddonInstance *load(const AddonInfo &info, AddonManager *manager) override;

private:
    StandardPath standardPath_;
    std::unordered_map<std::string, std::unique_ptr<AndroidSharedLibraryFactory>> registry_;
    AndroidLibraryDependency dependency_;
};

}

#endif //FCITX5_ANDROID_ANDROIDADDONLOADER_H
